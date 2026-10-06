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
import org.objectweb.asm.Opcodes;
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
 *   a class one mixin names is   "Bons and Furious: KEY: MIXIN is left out because CLASS is not installed; TARGET is left
 *   not installed                unchanged by it"   (since 1.0.34, REQUIRED_CLASS; the switch's other mixins apply)
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
    /**
     * Since 1.0.32: what the guards need from one class, taken from a single read for every switch that guards it (big
     * classes such as Level, ServerLevel or Entity are guarded by up to nine switches, and each read runs Forge's class
     * transformers). WANTED = class -> name+desc of every guarded method in the table ("*" = the declared-method shape);
     * PRINTS = class -> its fingerprints, or empty when the class does not exist.
     */
    private static final Map<String, Set<String>> WANTED = new LinkedHashMap<>();
    private static final Map<String, java.util.Optional<ClassPrints>> PRINTS = new ConcurrentHashMap<>();

    private record ClassPrints(Map<String, String> methods, String shape) {}
    /**
     * Since 1.0.28: methods a switch's fast path needs exactly as shipped, also after every other mod's mixins (its probe
     * mixin -> owner, name, desc). The probe is the switch's highest-priority mixin on that class, so Mixin applies it after
     * the others; postApply then compares the method with its guard fingerprint (afterApply / untouched).
     */
    private static final Map<String, String[]> UNTOUCHED_PROBES = Map.of(
            "bons.furious.mixin.cofh.DispatcherRenderersAccessor", new String[] {"net/minecraft/client/renderer/entity/EntityRenderDispatcher",
                    "m_114382_", "(Lnet/minecraft/world/entity/Entity;)Lnet/minecraft/client/renderer/entity/EntityRenderer;"});
    private static final Map<String, String> UNTOUCHED = new ConcurrentHashMap<>();   // key -> "" (untouched) or why not
    /**
     * Since 1.0.34: a mixin that names a class of a mod other than its target's (here a handler argument) -> that class.
     * When another mod also patches the target's superclass, Mixin resolves every class the mixin's members and calls
     * name while it merges them (MixinTargetContext.transformDescriptor -> ClassInfo.forName) and stops the game with
     * ClassMetadataNotFoundException if one does not exist, although the target class itself loads without that mod:
     * Pipez's GasPipeType without Mekanism next to Pipez Optimizer, which patches PipeType and loads GasPipeType from its
     * constructor. Such a mixin is left out when its class is missing; the code it changes cannot run without that class
     * either, so nothing else changes. Every other mixin that names another mod's class has a guard on that mod
     * (tools/foreign_types_gate.py in the build project checks a built jar).
     */
    private static final Map<String, String> REQUIRED_CLASS = Map.of(
            "bons.furious.mixin.pipez_logistics.GasPipeTypeFilterMixin", "mekanism/api/chemical/ChemicalStack");
    /**
     * Since 1.0.28: one call a switch redirects inside a static interface method, where Mixin 0.8.5 has no injectors
     * (carrier mixin -> method, desc, call owner, call name, call desc, new owner, new name). The carrier is an empty
     * mixin on that interface; preApply turns the call into INVOKESTATIC newOwner.newName(owner, args...) - the shape a
     * @Redirect handler has. The switch's guard fingerprints the method, so the call is there exactly once.
     */
    private static final Map<String, String[]> CALL_REWRITES = Map.of(
            "bons.furious.mixin.cofh.TranslucentRendererLookupMixin", new String[] {"renderTranslucent",
                    "(Lcom/mojang/blaze3d/vertex/PoseStack;FLnet/minecraft/client/renderer/LevelRenderer;Lorg/joml/Matrix4f;)V",
                    "net/minecraft/client/renderer/entity/EntityRenderDispatcher", "m_114382_",
                    "(Lnet/minecraft/world/entity/Entity;)Lnet/minecraft/client/renderer/entity/EntityRenderer;",
                    "bons/furious/patch/cofh/TranslucentRenderers", "renderer"});
    private static volatile boolean loaded;

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
            case APPLY -> {
                String needed = REQUIRED_CLASS.get(mixinClass);
                if (needed == null || prints(needed) != null) return true;
                if (LOGGED.add("required|" + mixinClass))
                    LOGGER.info("Bons and Furious: {}: {} is left out because {} is not installed; {} is left unchanged by it",
                            key, mixinClass.substring(mixinClass.lastIndexOf('.') + 1), needed.replace('/', '.'), target);
                return false;
            }
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

    /** IMixinConfigPlugin.preApply: for a carrier mixin (CALL_REWRITES), redirect its one call; anything else is left alone. */
    public static void beforeApply(String mixinClass, ClassNode target) {
        String[] r = CALL_REWRITES.get(mixinClass);
        String key = r == null ? null : keyOf(mixinClass);
        if (key == null) return;
        List<MethodInsnNode> calls = new ArrayList<>();
        for (MethodNode m : target.methods) {
            if (!m.name.equals(r[0]) || !m.desc.equals(r[1])) continue;
            for (AbstractInsnNode n : m.instructions) {
                if (n instanceof MethodInsnNode mi && mi.getOpcode() == Opcodes.INVOKEVIRTUAL && mi.owner.equals(r[2])
                        && mi.name.equals(r[3]) && mi.desc.equals(r[4])) calls.add(mi);
            }
        }
        if (calls.size() != 1) {
            LOGGER.warn("Bons and Furious: {} expected one {}.{} call in {}.{}, found {}; the method is left unchanged",
                    key, r[2].replace('/', '.'), r[3], target.name.replace('/', '.'), r[0], calls.size());
            return;
        }
        MethodInsnNode call = calls.get(0);
        call.setOpcode(Opcodes.INVOKESTATIC);
        call.owner = r[5];
        call.name = r[6];
        call.desc = "(L" + r[2] + ";" + r[4].substring(1);
        call.itf = false;
        LOGGER.debug("Bons and Furious: {}: {}.{} now asks {}.{}", key, target.name.replace('/', '.'), r[0], r[5].replace('/', '.'), r[6]);
    }

    /** IMixinConfigPlugin.postApply: for a probe mixin (UNTOUCHED_PROBES), whether its method is still the shipped one. */
    public static void afterApply(String mixinClass, ClassNode target) {
        String[] m = UNTOUCHED_PROBES.get(mixinClass);
        String key = m == null ? null : keyOf(mixinClass);
        if (key == null) return;
        String why;
        try {
            String expected = null;
            for (String[] g : GUARD.getOrDefault(key, List.of())) {
                if (g[0].equals(m[0]) && g[1].equals(m[1]) && g[2].equals(m[2])) expected = g[3];
            }
            MethodNode found = null;
            for (MethodNode x : target.methods) if (x.name.equals(m[1]) && x.desc.equals(m[2])) found = x;
            if (expected == null) why = "has no recorded fingerprint";
            else if (found == null) why = "is missing";
            else why = Fingerprint.of(found).equals(expected) ? "" : "is changed by another mod's mixin";
        } catch (Throwable t) {
            why = "could not be checked (" + t + ")";
        }
        UNTOUCHED.put(key, why);
        LOGGER.debug("Bons and Furious: {}: {}.{} {}", key, m[0].replace('/', '.'), m[1], why.isEmpty() ? "is untouched by other mods" : why);
    }

    /** "" when the switch's probed method was left as shipped by every other mod; the reason otherwise; null if never checked. */
    public static String untouched(String key) {
        return UNTOUCHED.get(key);
    }

    /**
     * Since 1.0.30: an injector of ours that stands down when another mod has replaced the method it works on (mixin ->
     * method, desc, handler name). Such an injector has require = 0 and a priority above the default, so Mixin lets it into
     * the other mod's @Overwrite, where it finds nothing to change; postApply then logs that the method is left to that mod.
     */
    private static final Map<String, String[]> STAND_DOWN = Map.of(
            "bons.furious.mixin.models.BoneLookupMixin", new String[] {"m_233393_", "(Ljava/lang/String;)Ljava/util/Optional;", "bons$partsWithBone"});

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
     * Since 1.0.30: a switch that replaces another mod's mixin (patches cancel list) steps aside while a third mod refines
     * that mixin through MixinSquared's @TargetHandler; without it, the third mod's injectors find no handler and the game
     * stops at start ("Critical injection failure ... @MixinSquared:Handler").
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
        List<String[]> guard = GUARD.getOrDefault(key, List.of());
        if (guard.isEmpty()) {
            if (!UNGUARDED.contains(key)) return new Decision(State.MISMATCH, "no fingerprints recorded for " + key);
            Decision h = handlerDecision(key);
            if (h != null) return h;
            Decision y = ForeignPatches.yieldDecision(YIELDS.get(key));
            return y != null ? y : new Decision(State.APPLY, "switched on");
        }
        // since 1.0.32: the target mod's own classes first; when all of them are missing the switch is ABSENT exactly as in
        // the full pass below (modded > 0 && moddedAbsent == modded), so the Minecraft classes it also guards are not read
        int moddedFirst = 0, moddedFirstAbsent = 0;
        for (String[] g : guard) {
            if (isGameClass(g[0])) continue;
            moddedFirst++;
            if (prints(g[0]) == null) moddedFirstAbsent++;
        }
        if (moddedFirst > 0 && moddedFirstAbsent == moddedFirst)
            return new Decision(State.ABSENT, "target mod " + MOD.getOrDefault(key, "?") + " is not installed");
        int absent = 0, modded = 0, moddedAbsent = 0;
        List<String> problems = new ArrayList<>();
        // since 1.0.34: modded classes missing because their whole package is in no module of the game (that mod is not
        // installed), as opposed to a missing class of an installed mod (another build of it). A switch that also guards
        // a second, optional mod (Fusion next to Embeddium, the camera mixins of six mods, ...) was MISMATCH with a WARN
        // that called the absent mod "another version"; it is ABSENT now, still off, with a debug line naming the package.
        List<String> uninstalled = new ArrayList<>();
        for (String[] g : guard) {
            boolean game = isGameClass(g[0]);
            if (!game) modded++;
            ClassPrints prints = prints(g[0]);
            if (prints == null) {
                absent++;
                if (!game) moddedAbsent++;
                if (!game && packageAbsent(g[0])) uninstalled.add(g[0]);
                problems.add(g[0].replace('/', '.') + " not found");
                continue;
            }
            if (g[1].equals("*")) {   // the class's set of declared methods (see Fingerprint.shape)
                if (!g[3].equals(prints.shape())) problems.add(g[0].replace('/', '.') + " declares other methods");
                continue;
            }
            String fp = prints.methods().get(g[1] + g[2]);
            if (fp == null) { problems.add(g[0].replace('/', '.') + "." + g[1] + " missing"); continue; }
            if (!fp.equals(g[3])) problems.add(g[0].replace('/', '.') + "." + g[1] + " differs");
        }
        // since 1.0.28: a switch that also guards Minecraft or Forge methods it relies on is ABSENT, not MISMATCH, when every
        // class of the target mod is missing (the mod is not installed; nothing about its version is wrong)
        if (absent == guard.size() || modded > 0 && moddedAbsent == modded)
            return new Decision(State.ABSENT, "target mod " + MOD.getOrDefault(key, "?") + " is not installed");
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

    /** Minecraft, Mojang and Forge classes: present in every pack, so their absence says nothing about the target mod. */
    private static boolean isGameClass(String internalName) {
        return internalName.startsWith("net/minecraft/") || internalName.startsWith("com/mojang/")
                || internalName.startsWith("net/minecraftforge/") || internalName.startsWith("java/");
    }

    /**
     * The fingerprints every switch's guards need from this class, from one read (null when the class does not exist).
     * The same method node gives the same Fingerprint.of value, so a decision is exactly what a read per switch gave. A
     * race between two threads only reads the class twice; the first stored result wins.
     */
    private static ClassPrints prints(String internalName) {
        java.util.Optional<ClassPrints> known = PRINTS.get(internalName);
        if (known != null) return known.orElse(null);
        ClassNode node = mayExist(internalName) ? read(internalName) : null;
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

    private static volatile Set<String> layerPackages;   // packages of our module's layer and all its parents; empty = unknown

    /**
     * Since 1.0.32: false only when the class cannot exist, so reading it is skipped. Mixin's bytecode provider
     * (MixinLaunchPluginLegacy.getClassNode) first asks the game layer's class loader, which serves a class only from the
     * module that owns its package (or from a parent layer that owns it), and on a miss falls back to
     * contextClassLoader.getResource(name + ".class"). For a package that no module owns, that class loader asks every jar
     * of the layer for the file (about two milliseconds per class in a 500-mod pack, ~28 ms per switch of a mod that is
     * not installed) and finds nothing, because a jar's module owns the package of every class file it holds. So a class
     * whose package is in no module of this layer or its parents is not found either way. Unknown layer -> always read.
     */
    private static boolean mayExist(String internalName) {
        Set<String> packages = layerPackages;
        if (packages == null) packages = loadLayerPackages();
        if (packages.isEmpty()) return true;
        int slash = internalName.lastIndexOf('/');
        return packages.contains(slash < 0 ? "" : internalName.substring(0, slash).replace('/', '.'));
    }

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
            if (layer != null && Guards.class.getModule().isNamed() && net.minecraftforge.fml.loading.FMLLoader.isProduction()) {
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
            LOGGER.debug("Bons and Furious: module layer packages unavailable ({}); every guarded class is read", t.toString());
        }
        layerPackages = Set.copyOf(out);
        return layerPackages;
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
