package bons.furious.guard;

import bons.pure.config.PureConfig;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.spongepowered.asm.service.MixinService;

/**
 * Decides, once per switch, whether that switch's mixins may be applied.
 *
 * Each switch lists the methods its mixins depend on, with the fingerprint of the tested mod build
 * (bons_and_furious.guards.tsv, written by the build from patches/*.json). Before the first mixin of a switch is
 * prepared, every listed method is read through Mixin's own bytecode provider and fingerprinted. The switch applies
 * only when all of them match; otherwise none of its mixins apply and the log gets one line per target class:
 *
 *   disabled in the config      "Bons and Furious: KEY is disabled by config; CLASS is left unchanged"
 *   target mod not installed    nothing (debug only)
 *   another build installed     "Bons and Furious: KEY skipped for CLASS because the installed class does not match
 *                                the supported version (...); the class is left unchanged"
 *   another mod does the same   "Bons and Furious: KEY steps aside: MOD VERSION makes the same change (MIXIN, option ...
 *                                on); CLASS is left unchanged by this switch"   (see ForeignPatches)
 *   another mod refines a mixin "Bons and Furious: KEY steps aside: MOD VERSION (MIXIN) changes FOREIGN_MIXIN, which this
 *   this switch would replace    switch would replace; CLASS is left unchanged by this switch"   (since 1.0.30)
 *
 * The decision is all or nothing per switch, so a patch spread over several classes never applies halfway.
 */
public final class Guards {
    public static final String RESOURCE = "/bons_and_furious.guards.tsv";
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    private static final Map<String, String> MIXIN_KEY = new LinkedHashMap<>();
    private static final Map<String, List<String[]>> GUARD = new LinkedHashMap<>();   // key -> [class, method, desc, sha256]
    private static final Map<String, String> MOD = new LinkedHashMap<>();
    private static final Map<String, String> CANCEL = new LinkedHashMap<>();           // another mod's mixin -> key
    private static final Map<String, List<ForeignPatches.Yield>> YIELDS = new LinkedHashMap<>();   // key -> the same patch in other mods
    private static final Map<String, ForeignPatches.Yield> YIELD_MIXIN = new LinkedHashMap<>();    // that mod's mixin -> its entry
    /** The switches whose mixins predate the guard table (PureMixinPlugin.MIXIN_KEYS): no fingerprints, the switch alone. */
    private static final Set<String> UNGUARDED = Set.of("frame_pacing", "terrain_density_memo", "vanilla_data_merge_unchanged");
    private static final Map<String, Decision> DECISIONS = new ConcurrentHashMap<>();
    private static final Set<String> LOGGED = ConcurrentHashMap.newKeySet();
    private static volatile boolean loaded;
    /**
     * Since 1.0.33: what the guards need from one class, taken from a single read for every switch that guards it (big
     * classes such as Level, ServerLevel or Entity are guarded by up to nine switches, and each read runs the loader's
     * class transformers). WANTED = class -> name+desc of every guarded method in the table ("*" = the declared-method
     * shape); PRINTS = class -> its fingerprints, or empty when the class does not exist. The Forge line also skips
     * reading classes whose package no module owns; this port does not need that: a switch whose target mod is not
     * installed is decided ABSENT from NeoForge's mod list before any class is read (compute below).
     */
    private static final Map<String, Set<String>> WANTED = new LinkedHashMap<>();
    private static final Map<String, java.util.Optional<ClassPrints>> PRINTS = new ConcurrentHashMap<>();

    private record ClassPrints(Map<String, String> methods, String shape) {}

    public enum State { APPLY, DISABLED, ABSENT, MISMATCH, YIELD }

    public record Decision(State state, String detail) {}

    private Guards() {}

    public static synchronized void load() {
        if (loaded) return;
        loaded = true;
        try (InputStream in = Guards.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                LOGGER.error("Bons and Furious: {} is missing from the jar; no patch will be applied", RESOURCE);
                return;
            }
            BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            while ((line = r.readLine()) != null) {
                if (line.isBlank() || line.startsWith("#")) continue;
                String[] f = line.split("\t");
                switch (f[0]) {
                    case "mixin" -> MIXIN_KEY.put(f[1], f[2]);
                    case "mod" -> MOD.put(f[1], f[2]);
                    case "guard" -> {
                        GUARD.computeIfAbsent(f[1], k -> new ArrayList<>()).add(new String[] {f[2], f[3], f[4], f[5]});
                        WANTED.computeIfAbsent(f[2], k -> new java.util.LinkedHashSet<>()).add(f[3].equals("*") ? "*" : f[3] + f[4]);
                    }
                    case "cancel" -> CANCEL.put(f[1], f[2]);
                    case "yield" -> {
                        ForeignPatches.Yield y = ForeignPatches.parse(f);
                        YIELDS.computeIfAbsent(y.key(), k -> new ArrayList<>()).add(y);
                        YIELD_MIXIN.put(y.mixin(), y);
                    }
                    default -> LOGGER.warn("Bons and Furious: unknown line in {}: {}", RESOURCE, line);
                }
            }
        } catch (Exception e) {
            LOGGER.error("Bons and Furious could not read {}; no patch will be applied", RESOURCE, e);
            MIXIN_KEY.clear();
            GUARD.clear();
            WANTED.clear();
            CANCEL.clear();
            YIELDS.clear();
            YIELD_MIXIN.clear();
        }
    }

    /** True when the mixin class belongs to a guarded switch (every mixin added in 1.0.20 does). */
    public static boolean knows(String mixinClass) {
        load();
        return MIXIN_KEY.containsKey(mixinClass);
    }

    public static String keyOf(String mixinClass) {
        load();
        return MIXIN_KEY.get(mixinClass);
    }

    /** IMixinConfigPlugin.shouldApplyMixin for a guarded mixin. */
    public static boolean shouldApply(String mixinClass, String targetClass) {
        String key = keyOf(mixinClass);
        if (key == null) return false;
        Decision d = decide(key);
        String target = targetClass.replace('/', '.');
        switch (d.state()) {
            case APPLY -> { return true; }
            case DISABLED -> {
                if (LOGGED.add(key + "|" + target))
                    LOGGER.info("Bons and Furious: {} is disabled by config; {} is left unchanged", key, target);
            }
            case ABSENT -> LOGGER.debug("Bons and Furious: {} not applied to {}: {}", key, target, d.detail());
            case MISMATCH -> {
                if (LOGGED.add(key + "|" + target))
                    LOGGER.warn("Bons and Furious: {} skipped for {} because the installed class does not match the supported version ({}); the class is left unchanged",
                            key, target, d.detail());
            }
            case YIELD -> logYield(key, target, d);
        }
        return false;
    }

    /**
     * For the switches without a guard table entry (PureMixinPlugin.MIXIN_KEYS), after their switch: true when another
     * mod makes the same change and the switch steps aside (logged once per class).
     */
    public static boolean stepsAside(String key, String targetClass) {
        load();
        Decision d = decide(key);
        if (d.state() != State.YIELD) return false;
        logYield(key, targetClass.replace('/', '.'), d);
        return true;
    }

    private static void logYield(String key, String target, Decision d) {
        if (LOGGED.add(key + "|" + target))
            LOGGER.info("Bons and Furious: {} steps aside: {}; {} is left unchanged by this switch", key, d.detail(), target);
    }

    /** MixinSquared's canceller: another mod's mixin is cancelled only while the switch that replaces it applies. */
    public static boolean shouldCancel(String foreignMixin) {
        load();
        String key = CANCEL.get(foreignMixin);
        if (key == null || decide(key).state() != State.APPLY) return false;
        ForeignPatches.Yield y = YIELD_MIXIN.get(foreignMixin);
        if (y != null && ForeignPatches.listedHere(y) && LOGGED.add("cancel|" + foreignMixin))
            LOGGER.info("Bons and Furious: {} applies; the same change in {} {} ({}{}) is left out, so only one copy runs",
                    key, y.name(), ForeignPatches.modVersion(y.mod()), y.shortMixin(), y.option() == null ? "" : ", option " + y.option() + " off");
        return true;
    }

    public static Decision decide(String key) {
        return DECISIONS.computeIfAbsent(key, Guards::compute);
    }

    /**
     * Since 1.0.30: an injector of ours that stands down when another mod has replaced
     * the method it works on (mixin -> method, desc, handler name; Mojang names on 1.21.1). Such an injector has
     * require = 0 and a priority above the default, so Mixin lets it into the other mod's @Overwrite, where it finds
     * nothing to change; postApply then logs that the method is left to that mod.
     */
    private static final Map<String, String[]> STAND_DOWN = Map.of(
            "bons.furious.mixin.models.BoneLookupMixin", new String[] {"getAnyDescendantWithName", "(Ljava/lang/String;)Ljava/util/Optional;", "bons$partsWithBone"});

    /** IMixinConfigPlugin.postApply: one line when a STAND_DOWN injector found the method replaced by another mod. */
    public static void checkStandDown(String mixinClass, ClassNode target) {
        String[] s = STAND_DOWN.get(mixinClass);
        String key = s == null ? null : keyOf(mixinClass);
        if (key == null) return;
        for (MethodNode m : target.methods) {
            if (!m.name.equals(s[0]) || !m.desc.equals(s[1])) continue;
            for (AbstractInsnNode n : m.instructions)
                if (n instanceof MethodInsnNode mi && mi.name.endsWith("$" + s[2])) return;   // our handler is called: applied
            String by = "another mod";
            if (m.visibleAnnotations != null)
                for (AnnotationNode a : m.visibleAnnotations)
                    if (a.desc.equals("Lorg/spongepowered/asm/mixin/transformer/meta/MixinMerged;") && a.values != null)
                        for (int i = 0; i + 1 < a.values.size(); i += 2) if ("mixin".equals(a.values.get(i))) by = String.valueOf(a.values.get(i + 1));
            if (LOGGED.add("standdown|" + key + "|" + target.name))
                LOGGER.info("Bons and Furious: {} steps aside: {} already replaces {}.{}; that method is left to it",
                        key, by, target.name.replace('/', '.'), s[0]);
            return;
        }
    }

    /**
     * Since 1.0.30: a switch that replaces another mod's mixin (patches cancel list)
     * steps aside while a third mod refines that mixin through MixinSquared's @TargetHandler; without it, the third mod's
     * injectors find no handler and the game stops at start ("Critical injection failure ... @MixinSquared:Handler").
     */
    private static Decision handlerDecision(String key) {
        Map<String, String> owners = new LinkedHashMap<>();   // every mixin a switch may cancel -> the mod that ships it
        for (Map.Entry<String, String> c : CANCEL.entrySet()) {
            ForeignPatches.Yield y = YIELD_MIXIN.get(c.getKey());
            owners.put(c.getKey(), y != null ? y.mod() : MOD.getOrDefault(c.getValue(), "?"));
        }
        for (Map.Entry<String, String> c : CANCEL.entrySet()) {
            if (!c.getValue().equals(key)) continue;
            String user = ForeignPatches.handlerUser(c.getKey(), owners);
            if (user != null) return new Decision(State.YIELD, user + " changes " + c.getKey() + ", which this switch would replace");
        }
        return null;
    }

    private static Decision compute(String key) {
        if (!PureConfig.isEnabled(key)) return new Decision(State.DISABLED, "disabled by config");
        // Mixed vanilla/mod guards must not look like an incompatible installed build when the optional mod is absent.
        String targetMod = MOD.get(key);
        var modList = net.neoforged.fml.loading.LoadingModList.get();
        if (targetMod != null && !targetMod.equals("minecraft") && !targetMod.equals("neoforge")
                && modList != null && modList.getModFileById(targetMod) == null)
            return new Decision(State.ABSENT, "target mod " + targetMod + " is not installed");
        // Embeddium 1.21.1 supplies its indexed implementation through a dynamic mixin list.
        // Let its own feature configuration own bone lookup, including a user's choice to disable it.
        if (key.equals("vanilla_model_bone_lookup") && modList != null && modList.getModFileById("embeddium") != null)
            return new Decision(State.YIELD, "Embeddium owns indexed model bone lookup on this Minecraft version");
        List<String[]> guard = GUARD.getOrDefault(key, List.of());
        if (guard.isEmpty()) {
            if (!UNGUARDED.contains(key)) return new Decision(State.MISMATCH, "no fingerprints recorded for " + key);
            Decision h = handlerDecision(key);
            if (h != null) return h;
            Decision y = ForeignPatches.yieldDecision(YIELDS.get(key));
            return y != null ? y : new Decision(State.APPLY, "switched on");
        }
        int absent = 0;
        List<String> problems = new ArrayList<>();
        // since 1.0.34 (as Forge 1.0.34): another mod's classes that are missing because their whole package is in no module
        // of the game (that mod is not installed); a switch that also needs such a mod is ABSENT, not MISMATCH
        List<String> uninstalled = new ArrayList<>();
        for (String[] g : guard) {
            ClassPrints prints = prints(g[0]);
            if (prints == null) {
                absent++;
                if (!isGameClass(g[0]) && packageAbsent(g[0])) uninstalled.add(g[0]);
                problems.add(g[0].replace('/', '.') + " not found");
                continue;
            }
            if (g[1].equals("*")) {   // the class's set of declared methods (see Fingerprint.shape)
                if (prints.shape() == null || !Fingerprint.matches(g[3], prints.shape())) problems.add(g[0].replace('/', '.') + " declares other methods");
                continue;
            }
            String fp = prints.methods().get(g[1] + g[2]);
            if (fp == null) { problems.add(g[0].replace('/', '.') + "." + g[1] + " missing"); continue; }
            if (!Fingerprint.matches(g[3], fp)) problems.add(g[0].replace('/', '.') + "." + g[1] + " differs");
        }
        if (absent == guard.size()) return new Decision(State.ABSENT, "target mod " + MOD.getOrDefault(key, "?") + " is not installed");
        if (!uninstalled.isEmpty() && problems.size() == uninstalled.size()) {
            String pkg = uninstalled.get(0).substring(0, Math.max(0, uninstalled.get(0).lastIndexOf('/'))).replace('/', '.');
            return new Decision(State.ABSENT, "a mod it also needs is not installed (package " + pkg + ")");
        }
        if (!problems.isEmpty()) return new Decision(State.MISMATCH, String.join(", ", problems.subList(0, Math.min(3, problems.size())))
                + (problems.size() > 3 ? " and " + (problems.size() - 3) + " more" : ""));
        Decision h = handlerDecision(key);
        if (h != null) return h;
        Decision y = ForeignPatches.yieldDecision(YIELDS.get(key));
        if (y != null) return y;
        return new Decision(State.APPLY, "all " + guard.size() + " fingerprints match");
    }

    private static boolean isGameClass(String internalName) {
        return internalName.startsWith("net/minecraft/") || internalName.startsWith("com/mojang/")
                || internalName.startsWith("net/neoforged/") || internalName.startsWith("java/");
    }

    private static volatile Set<String> layerPackages;   // packages of our module's layer and all its parents; empty = unknown

    /** Since 1.0.34: true only when the layer's packages are known and none of them is this class's package. */
    private static boolean packageAbsent(String internalName) {
        Set<String> packages = layerPackages;
        if (packages == null) packages = loadLayerPackages();
        if (packages.isEmpty()) return false;   // unknown (offline tools, development): never assume a mod is missing
        int slash = internalName.lastIndexOf('/');
        return !packages.contains(slash < 0 ? "" : internalName.substring(0, slash).replace('/', '.'));
    }

    private static synchronized Set<String> loadLayerPackages() {
        if (layerPackages != null) return layerPackages;
        Set<String> out = new java.util.HashSet<>();
        try {
            ModuleLayer layer = Guards.class.getModule().getLayer();
            if (layer != null && Guards.class.getModule().isNamed() && net.neoforged.fml.loading.FMLLoader.isProduction()) {
                java.util.ArrayDeque<ModuleLayer> todo = new java.util.ArrayDeque<>(List.of(layer));
                Set<ModuleLayer> seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
                while (!todo.isEmpty()) {
                    ModuleLayer l = todo.pop();
                    if (!seen.add(l)) continue;
                    for (Module m : l.modules()) out.addAll(m.getPackages());
                    todo.addAll(l.parents());
                }
            }
        } catch (Throwable t) {
            out.clear();
            LOGGER.debug("Bons and Furious: module layer packages unavailable ({}); every missing class counts as a mismatch", t.toString());
        }
        layerPackages = Set.copyOf(out);
        return layerPackages;
    }

    /**
     * The fingerprints every switch's guards need from this class, from one read (null when the class does not exist).
     * The same method node gives the same Fingerprint.of value, so a decision is exactly what a read per switch gave. A
     * race between two threads only reads the class twice; the first stored result wins.
     */
    private static ClassPrints prints(String internalName) {
        java.util.Optional<ClassPrints> known = PRINTS.get(internalName);
        if (known != null) return known.orElse(null);
        ClassNode node = read(internalName);
        ClassPrints p = null;
        if (node != null) {
            Map<String, String> methods = new java.util.HashMap<>();
            String shape = null;
            for (String want : WANTED.getOrDefault(internalName, Set.of())) {
                if (want.equals("*")) { shape = Fingerprint.shape(node); continue; }
                for (MethodNode x : node.methods) {
                    if ((x.name + x.desc).equals(want)) { methods.put(want, Fingerprint.of(x)); break; }
                }
            }
            p = new ClassPrints(methods, shape);
        }
        PRINTS.putIfAbsent(internalName, java.util.Optional.ofNullable(p));
        return PRINTS.get(internalName).orElse(null);
    }

    private static ClassNode read(String internalName) {
        try {
            return MixinService.getService().getBytecodeProvider().getClassNode(internalName.replace('/', '.'));
        } catch (ClassNotFoundException e) {
            return null;
        } catch (Throwable t) {
            LOGGER.debug("Bons and Furious: could not read {}: {}", internalName, t.toString());
            return null;
        }
    }
}
