package bons.furious.compat;

import bons.furious.guard.Guards;
import bons.pure.config.PureConfig;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.commons.lang3.SystemUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * Performance options that other mods switch off whenever C2ME is installed, given back while the C2ME part that
 * would clash with them is switched off or does not exist.
 *
 *  - Radium Re-Reforged 0.14.3 turns off mixin.world.chunk_access and mixin.world.player_chunk_tick when the C2ME sub-mods
 *    c2me_opts_chunk_access and c2me_notickvd are installed (LithiumConfig.applyC2MECompat). The all-in-one C2ME jar
 *    always installs both, also when c2me.toml switches those modules off; then neither version runs. These come back
 *    when the module's own ModuleEntryPoint.enabled (the value C2ME's module manager reads) is false.
 *  - ModernFix 5.27.77 turns off mixin.perf.cache_strongholds when C2ME is installed (ModernFixEarlyConfig). C2ME
 *    0.2.0+alpha.12 does not touch concentric-ring (stronghold) placement at all, so with that C2ME build the cache
 *    comes back.
 *  - Distant Horizons 3.3.2 stops reading saved chunks itself when C2ME is installed (ChunkFileReader_forge) and queues
 *    them on Minecraft's single chunk IO thread instead. The direct reads come back while C2ME's three chunk-IO modules
 *    (ioSystem.replaceImpl, ioSystem.async, ioSystem.gcFreeChunkSerializer) are off, read from their own enabled flags
 *    (DistantHorizonsDirectReadsMixin, switch distanthorizons_c2me_direct_reads).
 *  - Radium's mixin.experimental.chunk_tickets and mixin.experimental.spawning (both default-on in later Lithium) cannot be
 *    switched on in lithium.properties without the rest of mixin.experimental: Radium's config copies a user-set parent's
 *    value onto every child (CaffeineConfig.applyChildOptionsStateChecks), so "mixin.experimental=true" also enables the
 *    entity block caching (whose block_touching part skips Valkyrien Skies', Bumblezone's and Dimensional Doors' hooks),
 *    whatever the child lines say. Set here on the live options, after that copy ran: the group on, its entity part off
 *    (switch radium_experimental_tickets_spawning, trigger RadiumExperimentalOptionTrigger).
 *
 * Radium and ModernFix read these options live while Mixin prepares their configs. This config has priority 900, so
 * Mixin prepares it after every config plugin has loaded (Radium and ModernFix build their options while loading) and
 * before theirs (default priority 1000). The trigger mixins only exist to get that call; they are never applied. The
 * mixins listed after them are decided with the options as they are after the triggers:
 *
 *  - RadiumChunkSchedulingMixin goes with a restored mixin.world.chunk_access (radium_c2me_chunk_access);
 *  - RadiumUntrackHookMixin (radium_untrack_chunk_hooks) goes with Radium's player-chunk-tick mixin whenever that one
 *    applies, restored or not;
 *  - 1.0.34: RadiumExpiringTicketNullGuardMixin (radium_experimental_tickets_spawning), listed after
 *    RadiumExperimentalOptionTrigger, goes with Radium's experimental chunk_tickets mixin whenever that one applies.
 *
 * Nothing changes when the C2ME part is on, when C2ME or the other mod is absent, when the user's own config sets the
 * option, when another mod also disabled it, when a switch is off, or when a guarded method differs from the tested build.
 * 1.0.34: for ModernFix the user's own setting is read from the sources ModernFix reads (modernFixUserSetting), because
 * ModernFix ignores a user setting of an option a mod already switched off and does not mark the option as user-set.
 */
public final class C2meCompatPlugin implements IMixinConfigPlugin {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    private static final String PACKAGE = "bons.furious.mixin.c2me_compat.";
    static final String SCHEDULING_MIXIN = PACKAGE + "RadiumChunkSchedulingMixin";
    static final String UNTRACK_MIXIN = PACKAGE + "RadiumUntrackHookMixin";
    static final String UNTRACK_KEY = "radium_untrack_chunk_hooks";
    static final String STRONGHOLD_KEY = "modernfix_c2me_cache_strongholds";
    /** The C2ME build checked for concentric-ring placement code (none in any of its 20 module jars). */
    static final String C2ME_TESTED = "0.2.0+alpha.12";

    /** One Radium option given back while one C2ME module is off. */
    record Restore(String key, String option, Set<String> c2meMods, String entryPoint, String c2meSetting) {}

    static final Map<String, Restore> RADIUM_TRIGGERS = Map.of(
            PACKAGE + "ChunkAccessOptionTrigger", new Restore("radium_c2me_chunk_access", "mixin.world.chunk_access",
                    Set.of("c2me_opts_chunk_access", "c2me-opts-chunk-access"), "com.ishland.c2me.opts.chunk_access.ModuleEntryPoint",
                    "generalOptimizations.optimizeAsyncChunkRequest"),
            PACKAGE + "PlayerChunkTickOptionTrigger", new Restore("radium_c2me_player_chunk_tick", "mixin.world.player_chunk_tick",
                    Set.of("c2me_notickvd", "c2me-notickvd"), "com.ishland.c2me.notickvd.ModuleEntryPoint",
                    "noTickViewDistance.enabled"));
    static final String STRONGHOLD_TRIGGER = PACKAGE + "StrongholdCacheOptionTrigger";
    static final String EXPERIMENTAL_TRIGGER = PACKAGE + "RadiumExperimentalOptionTrigger";
    static final String EXPERIMENTAL_KEY = "radium_experimental_tickets_spawning";
    /** 1.0.34: the null check in Radium's experimental chunk_tickets handler (radium_experimental_tickets_spawning). */
    static final String NULL_GUARD_MIXIN = PACKAGE + "RadiumExpiringTicketNullGuardMixin";
    static final String DH_MIXIN = PACKAGE + "DistantHorizonsDirectReadsMixin";
    static final String DH_KEY = "distanthorizons_c2me_direct_reads";
    /** C2ME's chunk-IO modules (entry point, c2me.toml key): any one of them on keeps Distant Horizons on the IO thread. */
    static final String[][] C2ME_CHUNK_IO = {
            {"com.ishland.c2me.rewrites.chunkio.ModuleEntryPoint", "ioSystem.replaceImpl"},
            {"com.ishland.c2me.threading.chunkio.ModuleEntryPoint", "ioSystem.async"},
            {"com.ishland.c2me.rewrites.chunk_serializer.ModuleEntryPoint", "ioSystem.gcFreeChunkSerializer"}};

    private static final Set<String> CHECKED = ConcurrentHashMap.newKeySet();
    private static final Set<String> RESTORED = ConcurrentHashMap.newKeySet();
    private static final Map<String, Boolean> APPLY = new ConcurrentHashMap<>();

    @Override
    public void onLoad(String mixinPackage) {
        PureConfig.load();
        Guards.load();
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        Restore r = RADIUM_TRIGGERS.get(mixinClassName);
        if (r != null) {
            if (CHECKED.add(r.key())) {
                try {
                    if (restoreRadium(r)) RESTORED.add(r.key());
                } catch (Throwable t) {
                    LOGGER.warn("Bons and Furious: {} could not check Radium's {} ({}); Radium's options are left as Radium set them",
                            r.key(), r.option(), t.toString());
                }
            }
            return false;   // a trigger is never applied
        }
        if (mixinClassName.equals(STRONGHOLD_TRIGGER)) {
            if (CHECKED.add(STRONGHOLD_KEY)) {
                try {
                    if (restoreStrongholdCache()) RESTORED.add(STRONGHOLD_KEY);
                } catch (Throwable t) {
                    LOGGER.warn("Bons and Furious: {} could not check ModernFix's mixin.perf.cache_strongholds ({}); ModernFix's options are left as ModernFix set them",
                            STRONGHOLD_KEY, t.toString());
                }
            }
            return false;
        }
        if (mixinClassName.equals(EXPERIMENTAL_TRIGGER)) {
            if (CHECKED.add(EXPERIMENTAL_KEY)) {
                try {
                    if (restoreRadiumExperimental()) RESTORED.add(EXPERIMENTAL_KEY);
                } catch (Throwable t) {
                    LOGGER.warn("Bons and Furious: {} could not check Radium's experimental options ({}); Radium's options are left as Radium set them",
                            EXPERIMENTAL_KEY, t.toString());
                }
            }
            return false;
        }
        if (mixinClassName.equals(SCHEDULING_MIXIN)) return RESTORED.contains("radium_c2me_chunk_access");
        if (mixinClassName.equals(UNTRACK_MIXIN)) return APPLY.computeIfAbsent(UNTRACK_KEY, k -> untrackHook());
        if (mixinClassName.equals(DH_MIXIN)) return APPLY.computeIfAbsent(DH_KEY, k -> dhDirectReads());
        if (mixinClassName.equals(NULL_GUARD_MIXIN)) return APPLY.computeIfAbsent(NULL_GUARD_MIXIN, k -> expiringTicketNullGuard());   // 1.0.34
        return false;
    }

    // ---------------------------------------------------------------- Distant Horizons

    /** Distant Horizons keeps its direct saved-chunk reads while C2ME's chunk IO is Minecraft's own; true when it does. */
    static boolean dhDirectReads() {
        try {
            if (modVersion("distanthorizons") == null) {
                LOGGER.debug("Bons and Furious: {} has nothing to do: Distant Horizons is not installed", DH_KEY);
                return false;
            }
            String c2me = modVersion("c2me");
            if (c2me == null) {
                LOGGER.debug("Bons and Furious: {} has nothing to do: C2ME is not installed, so Distant Horizons reads saved chunks itself", DH_KEY);
                return false;
            }
            if (!C2ME_TESTED.equals(c2me)) {
                LOGGER.info("Bons and Furious: {} leaves Distant Horizons on Minecraft's chunk IO thread: C2ME {} is installed, and only {} was checked",
                        DH_KEY, c2me, C2ME_TESTED);
                return false;
            }
            ClassLoader loader = C2meCompatPlugin.class.getClassLoader();
            for (String[] m : C2ME_CHUNK_IO) {
                Object on = declaredStaticField(loader, m[0], "enabled");
                if (!(on instanceof Boolean b)) {
                    LOGGER.warn("Bons and Furious: {} could not read whether C2ME's {} is on ({}.enabled); Distant Horizons stays on Minecraft's chunk IO thread",
                            DH_KEY, m[1], m[0]);
                    return false;
                }
                if (b) {
                    LOGGER.info("Bons and Furious: {} leaves Distant Horizons on Minecraft's chunk IO thread: C2ME's {} is on", DH_KEY, m[1]);
                    return false;
                }
            }
            if (!guarded(DH_KEY, "Distant Horizons stays on Minecraft's chunk IO thread")) return false;
            LOGGER.info("Bons and Furious: {} keeps Distant Horizons reading saved chunks itself: C2ME's chunk IO is Minecraft's own "
                    + "(ioSystem.replaceImpl, ioSystem.async and ioSystem.gcFreeChunkSerializer are off)", DH_KEY);
            return true;
        } catch (Throwable t) {
            LOGGER.warn("Bons and Furious: {} could not check C2ME's chunk IO modules ({}); Distant Horizons stays on Minecraft's chunk IO thread",
                    DH_KEY, t.toString());
            return false;
        }
    }

    private static Object declaredStaticField(ClassLoader loader, String className, String field) throws ReflectiveOperationException {
        Class<?> c;
        try {
            c = Class.forName(className, true, loader);
        } catch (ClassNotFoundException e) {
            return null;
        }
        Field f = c.getDeclaredField(field);
        f.setAccessible(true);
        return f.get(null);
    }

    // ---------------------------------------------------------------- Radium

    /** Gives Radium its option back; true when it did. */
    static boolean restoreRadium(Restore r) throws ReflectiveOperationException {
        ClassLoader loader = C2meCompatPlugin.class.getClassLoader();
        Object config = radiumConfig(loader);
        if (config == null) {
            LOGGER.debug("Bons and Furious: {} has nothing to do: Radium is not installed or has not built its options", r.key());
            return false;
        }
        Object option = config.getClass().getMethod("getOption", String.class).invoke(config, r.option());
        if (option == null) {
            LOGGER.debug("Bons and Furious: {} has nothing to do: Radium has no option {}", r.key(), r.option());
            return false;
        }
        Class<?> o = option.getClass();
        if ((Boolean) o.getMethod("isEnabled").invoke(option)) {
            LOGGER.debug("Bons and Furious: {} has nothing to do: Radium's {} is already on", r.key(), r.option());
            return false;
        }
        if ((Boolean) o.getMethod("isUserDefined").invoke(option)) {
            LOGGER.info("Bons and Furious: {} leaves Radium's {} off because Radium's own config file sets it", r.key(), r.option());
            return false;
        }
        Collection<?> definers = (Collection<?>) o.getMethod("getDefiningMods").invoke(option);
        if (!onlyBy(definers, r.c2meMods(), r.key(), "Radium's " + r.option())) return false;
        Object c2meOn = staticField(loader, r.entryPoint(), "enabled");
        if (!(c2meOn instanceof Boolean on)) {
            LOGGER.warn("Bons and Furious: {} could not read whether C2ME's module is on ({}.enabled); Radium's {} stays off",
                    r.key(), r.entryPoint(), r.option());
            return false;
        }
        if (on) {
            LOGGER.info("Bons and Furious: {} leaves Radium's {} off: C2ME's own version is on ({} is not false)", r.key(), r.option(), r.c2meSetting());
            return false;
        }
        if (!guarded(r.key(), "Radium's " + r.option() + " stays off")) return false;
        o.getMethod("clearModsDefiningValue").invoke(option);
        o.getMethod("addModOverride", boolean.class, String.class).invoke(option, true, "bons_and_furious");
        LOGGER.info("Bons and Furious: {} switched Radium's {} back on: the C2ME module that replaces it is off ({} = false)",
                r.key(), r.option(), r.c2meSetting());
        return true;
    }

    /** The untrack hook goes with Radium's player-chunk-tick mixin, decided exactly as Radium's plugin will decide it. */
    static boolean untrackHook() {
        try {
            Object config = radiumConfig(C2meCompatPlugin.class.getClassLoader());
            if (config == null) return false;
            Object option = config.getClass().getMethod("getEffectiveOptionForMixin", String.class)
                    .invoke(config, "world.player_chunk_tick.ThreadedAnvilChunkStorageMixin");
            if (option == null || !(Boolean) option.getClass().getMethod("isEnabled").invoke(option)) {
                LOGGER.debug("Bons and Furious: {} has nothing to do: Radium's player-chunk-tick mixin is not applied", UNTRACK_KEY);
                return false;
            }
        } catch (Throwable t) {
            LOGGER.warn("Bons and Furious: {} could not check Radium's player-chunk-tick option ({}); ChunkMap is left as Radium makes it",
                    UNTRACK_KEY, t.toString());
            return false;
        }
        return guarded(UNTRACK_KEY, "ChunkMap is left as Radium makes it");
    }

    /** Radium's experimental chunk_tickets + spawning on, its experimental entity block caching off; true when it did. */
    static boolean restoreRadiumExperimental() throws ReflectiveOperationException {
        Object config = radiumConfig(C2meCompatPlugin.class.getClassLoader());
        if (config == null) {
            LOGGER.debug("Bons and Furious: {} has nothing to do: Radium is not installed or has not built its options", EXPERIMENTAL_KEY);
            return false;
        }
        java.lang.reflect.Method getOption = config.getClass().getMethod("getOption", String.class);
        String[] names = {"mixin.experimental", "mixin.experimental.entity", "mixin.experimental.chunk_tickets", "mixin.experimental.spawning"};
        Object[] o = new Object[names.length];
        for (int i = 0; i < names.length; i++) {
            o[i] = getOption.invoke(config, names[i]);
            if (o[i] == null) {
                LOGGER.debug("Bons and Furious: {} has nothing to do: Radium has no option {}", EXPERIMENTAL_KEY, names[i]);
                return false;
            }
        }
        Class<?> c = o[0].getClass();
        for (int i = 0; i < names.length; i++) {
            if ((Boolean) c.getMethod("isUserDefined").invoke(o[i])) {
                LOGGER.info("Bons and Furious: {} leaves Radium's experimental options as Radium's own config sets them ({})", EXPERIMENTAL_KEY, names[i]);
                return false;
            }
            if ((Boolean) c.getMethod("isModDefined").invoke(o[i])) {
                LOGGER.info("Bons and Furious: {} leaves Radium's experimental options off: {} is set by {}", EXPERIMENTAL_KEY, names[i],
                        c.getMethod("getDefiningMods").invoke(o[i]));
                return false;
            }
        }
        if ((Boolean) c.getMethod("isEnabled").invoke(o[0])) {
            LOGGER.debug("Bons and Furious: {} has nothing to do: Radium's mixin.experimental is already on", EXPERIMENTAL_KEY);
            return false;
        }
        if (!(Boolean) c.getMethod("isEnabled").invoke(o[2]) || !(Boolean) c.getMethod("isEnabled").invoke(o[3])) {
            LOGGER.info("Bons and Furious: {} leaves Radium's experimental options off: Radium's own defaults switch chunk_tickets or spawning off", EXPERIMENTAL_KEY);
            return false;
        }
        if (!guarded(EXPERIMENTAL_KEY, "Radium's experimental options stay off")) return false;
        java.lang.reflect.Method override = c.getMethod("addModOverride", boolean.class, String.class);
        override.invoke(o[1], false, "bons_and_furious");   // first the entity part off, then the group on
        override.invoke(o[0], true, "bons_and_furious");
        LOGGER.info("Bons and Furious: {} switched Radium's mixin.experimental.chunk_tickets and mixin.experimental.spawning on; "
                + "mixin.experimental.entity (entity block caching) stays off", EXPERIMENTAL_KEY);
        return true;
    }

    /**
     * 1.0.34: the null guard goes with Radium's experimental chunk_tickets mixin whenever that one applies, decided exactly
     * as Radium's plugin will decide it (after RadiumExperimentalOptionTrigger, listed before it, set the options), while
     * the switch applies.
     */
    static boolean expiringTicketNullGuard() {
        try {
            Object config = radiumConfig(C2meCompatPlugin.class.getClassLoader());
            if (config == null) return false;
            Object option = config.getClass().getMethod("getEffectiveOptionForMixin", String.class)
                    .invoke(config, "experimental.chunk_tickets.ChunkTicketManagerMixin");
            if (option == null || !(Boolean) option.getClass().getMethod("isEnabled").invoke(option)) {
                LOGGER.debug("Bons and Furious: {} has no ticket handler to guard: Radium's experimental chunk_tickets mixin is not applied", EXPERIMENTAL_KEY);
                return false;
            }
        } catch (Throwable t) {
            LOGGER.warn("Bons and Furious: {} could not check Radium's experimental chunk_tickets option ({}); Radium's ticket handler is left as Radium makes it",
                    EXPERIMENTAL_KEY, t.toString());
            return false;
        }
        return guarded(EXPERIMENTAL_KEY, "Radium's ticket handler is left as Radium makes it");
    }

    /** Radium's live CaffeineConfig (the options its mixin plugin reads), or null when Radium is absent or not loaded. */
    private static Object radiumConfig(ClassLoader loader) throws ReflectiveOperationException {
        Object plugin = staticField(loader, "me.jellysquid.mods.lithium.common.LithiumMod", "CONFIG");
        if (plugin == null) return null;
        Field f = Class.forName("net.caffeinemc.caffeineconfig.AbstractCaffeineConfigMixinPlugin", false, loader).getDeclaredField("config");
        f.setAccessible(true);
        return f.get(plugin);
    }

    // ---------------------------------------------------------------- ModernFix

    /** Gives ModernFix its stronghold-position cache back; true when it did. */
    static boolean restoreStrongholdCache() throws ReflectiveOperationException {
        ClassLoader loader = C2meCompatPlugin.class.getClassLoader();
        Object plugin = staticField(loader, "org.embeddedt.modernfix.core.ModernFixMixinPlugin", "instance");
        if (plugin == null) {
            LOGGER.debug("Bons and Furious: {} has nothing to do: ModernFix is not installed or has not loaded its options", STRONGHOLD_KEY);
            return false;
        }
        Object config = plugin.getClass().getField("config").get(plugin);
        Map<?, ?> options = (Map<?, ?>) config.getClass().getMethod("getOptionMap").invoke(config);
        Object option = options.get("mixin.perf.cache_strongholds");
        if (option == null) {
            LOGGER.debug("Bons and Furious: {} has nothing to do: ModernFix has no option mixin.perf.cache_strongholds", STRONGHOLD_KEY);
            return false;
        }
        Class<?> o = option.getClass();
        if (Boolean.TRUE.equals(o.getMethod("getValue").invoke(option))) {
            LOGGER.debug("Bons and Furious: {} has nothing to do: ModernFix's mixin.perf.cache_strongholds is already on", STRONGHOLD_KEY);
            return false;
        }
        if ((Boolean) o.getMethod("isUserDefined").invoke(option)) {
            LOGGER.info("Bons and Furious: {} leaves ModernFix's mixin.perf.cache_strongholds off because ModernFix's own config sets it", STRONGHOLD_KEY);
            return false;
        }
        String userSetting = modernFixUserSetting("mixin.perf.cache_strongholds");   // 1.0.34
        if (userSetting != null) {
            LOGGER.info("Bons and Furious: {} leaves ModernFix's mixin.perf.cache_strongholds off because the user sets it ({}); ModernFix ignores "
                    + "a user setting of an option a mod switched off", STRONGHOLD_KEY, userSetting);
            return false;
        }
        Collection<?> definers = (Collection<?>) o.getMethod("getDefiningMods").invoke(option);
        if (!onlyBy(definers, Set.of("c2me"), STRONGHOLD_KEY, "ModernFix's mixin.perf.cache_strongholds")) return false;
        String c2me = modVersion("c2me");
        if (!C2ME_TESTED.equals(c2me)) {
            LOGGER.warn("Bons and Furious: {} leaves ModernFix's mixin.perf.cache_strongholds off: C2ME {} is installed, and only {} was checked for stronghold placement code",
                    STRONGHOLD_KEY, c2me, C2ME_TESTED);
            return false;
        }
        if (!guarded(STRONGHOLD_KEY, "ModernFix's mixin.perf.cache_strongholds stays off")) return false;
        o.getMethod("addModOverride", Object.class, String.class).invoke(option, Boolean.TRUE, "bons_and_furious");
        LOGGER.info("Bons and Furious: {} switched ModernFix's mixin.perf.cache_strongholds back on: C2ME {} does not touch stronghold placement",
                STRONGHOLD_KEY, c2me);
        return true;
    }

    /**
     * 1.0.34: where the user sets a ModernFix option, or null. ModernFixEarlyConfig.load reads ./config/modernfix-mixins.properties,
     * then the global file <minecraft folder>/global/modernfix-global-mixins.properties, then -Dmodernfix.config.<option> (none
     * of them under -Dmodernfix.ignoreConfigForTesting=true). For an option a mod already switched off it skips the files'
     * lines, and a JVM value equal to the current one changes nothing, so Option.isUserDefined stays false although the user
     * set the option; the same sources are read here instead. ModernFix rewrites its own file while it loads, before this
     * check, keeping only the options it took as user-set, so a line it skipped there is normally gone by now; the global
     * file and the JVM property stay as the user wrote them.
     */
    static String modernFixUserSetting(String option) {
        if (Boolean.getBoolean("modernfix.ignoreConfigForTesting")) return null;
        if (propertiesSet(Paths.get("config", "modernfix-mixins.properties"), option)) return "config/modernfix-mixins.properties";
        try {
            Path minecraft = SystemUtils.IS_OS_MAC ? Paths.get(System.getProperty("user.home"), "Library", "Application Support", "minecraft")
                    : SystemUtils.IS_OS_WINDOWS ? Paths.get(System.getenv("APPDATA"), ".minecraft") : Paths.get(System.getProperty("user.home"), ".minecraft");
            Path global = minecraft.resolve("global").resolve("modernfix-global-mixins.properties");
            if (propertiesSet(global, option)) return global.toString();
        } catch (RuntimeException e) {
            // no global folder to resolve: ModernFix reads no global file either (it logs that and goes on)
        }
        String jvm = System.getProperty("modernfix.config." + option);
        return jvm == null || jvm.isEmpty() ? null : "-Dmodernfix.config." + option + "=" + jvm;
    }

    /** True when the properties file exists, can be read and has the key (java.util.Properties, as ModernFix reads it). */
    private static boolean propertiesSet(Path file, String key) {
        if (!Files.isRegularFile(file)) return false;
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(file)) {
            p.load(in);
        } catch (IOException | RuntimeException e) {
            return false;
        }
        return p.containsKey(key);
    }

    private static String modVersion(String modId) {
        try {
            var file = net.minecraftforge.fml.loading.LoadingModList.get().getModFileById(modId);
            return file == null ? null : file.getMods().stream().filter(m -> m.getModId().equals(modId)).findFirst()
                    .map(m -> m.getVersion().toString()).orElse(null);
        } catch (Throwable t) {
            return null;
        }
    }

    // ---------------------------------------------------------------- shared

    private static boolean onlyBy(Collection<?> definers, Set<String> allowed, String key, String what) {
        if (!definers.isEmpty() && allowed.containsAll(definers.stream().map(String::valueOf).toList())) return true;
        LOGGER.info("Bons and Furious: {} leaves {} off because it was switched off by {}, not only by C2ME", key, what,
                definers.isEmpty() ? "the mod's defaults" : String.join(", ", definers.stream().map(String::valueOf).toList()));
        return false;
    }

    private static boolean guarded(String key, String otherwise) {
        Guards.Decision d = Guards.decide(key);
        switch (d.state()) {
            case APPLY -> { return true; }
            case DISABLED -> LOGGER.info("Bons and Furious: {} is disabled by config; {}", key, otherwise);
            case ABSENT -> LOGGER.debug("Bons and Furious: {} has nothing to do: {}", key, d.detail());
            case MISMATCH -> LOGGER.warn("Bons and Furious: {} skipped because an installed class does not match the supported version ({}); {}",
                    key, d.detail(), otherwise);
        }
        return false;
    }

    private static Object staticField(ClassLoader loader, String className, String field) throws ReflectiveOperationException {
        Class<?> c;
        try {
            c = Class.forName(className, true, loader);
        } catch (ClassNotFoundException e) {
            return null;
        }
        return c.getField(field).get(null);
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        if (mixinClassName.equals(SCHEDULING_MIXIN)) LOGGER.debug("Bons and Furious: radium_c2me_chunk_access applied to {}", targetClassName);
        if (mixinClassName.equals(UNTRACK_MIXIN)) LOGGER.debug("Bons and Furious: {} applied to {}", UNTRACK_KEY, targetClassName);
        if (mixinClassName.equals(DH_MIXIN)) LOGGER.debug("Bons and Furious: {} applied to {}", DH_KEY, targetClassName);
        if (mixinClassName.equals(NULL_GUARD_MIXIN)) LOGGER.debug("Bons and Furious: {} (null guard) applied to {}", EXPERIMENTAL_KEY, targetClassName);   // 1.0.34
    }
}
