package bons.furious.compat;

import bons.furious.guard.Guards;
import bons.pure.config.PureConfig;
import java.lang.reflect.Field;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
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
 *
 * Radium and ModernFix read these options live while Mixin prepares their configs. This config has priority 900, so
 * Mixin prepares it after every config plugin has loaded (Radium and ModernFix build their options while loading) and
 * before theirs (default priority 1000). The trigger mixins only exist to get that call; they are never applied. The
 * two mixins listed after them are decided with the options as they are after the triggers:
 *
 *  - RadiumChunkSchedulingMixin goes with a restored mixin.world.chunk_access (radium_c2me_chunk_access);
 *  - RadiumUntrackHookMixin (radium_untrack_chunk_hooks) goes with Radium's player-chunk-tick mixin whenever that one
 *    applies, restored or not.
 *
 * Nothing changes when the C2ME part is on, when C2ME or the other mod is absent, when the user's own config sets the
 * option, when another mod also disabled it, when a switch is off, or when a guarded method differs from the tested build.
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
        if (mixinClassName.equals(SCHEDULING_MIXIN)) return RESTORED.contains("radium_c2me_chunk_access");
        if (mixinClassName.equals(UNTRACK_MIXIN)) return APPLY.computeIfAbsent(UNTRACK_KEY, k -> untrackHook());
        return false;
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
    }
}
