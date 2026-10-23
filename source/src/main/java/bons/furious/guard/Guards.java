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
 *   another mod cancels the mixin "Bons and Furious: KEY steps aside: MOD VERSION (CANCELLER) cancels OWNER's MIXIN, the mixin
 *   the switch changes           this switch changes; CLASS is left unchanged by this switch"   (since 1.0.38)
 *   the target mod fixed it      "Bons and Furious: KEY: fixed upstream in MOD VERSION, stands down (installed MOD VERSION;
 *   upstream (redundant_since)    NOTE); CLASS is left unchanged"   (since 1.0.46)

 *
 * The decision is all or nothing per switch, so a patch spread over several classes never applies halfway.
 *
 * Since 1.0.46 a switch may accept more than one build of its targets ("accepted alternative fingerprints"): each guard
 * line can carry, besides the tested build's fingerprint ("-" = the method must be absent there), one fingerprint per
 * other build label ("label=sha256;label=-"). The switch applies when ONE label matches ALL its guard lines: the tested
 * build, or a label L (a line without an entry for L is unchanged in L). Builds are never mixed across lines. A switch
 * whose patch the target mod made redundant keeps its old fingerprints and lists "redundant" lines (mod, first fixed
 * version, even-if-matching flag, note): on that mod at or above that version it stands down with an INFO line instead
 * of the mismatch WARN (with the flag set also while every fingerprint matches). A guard line listed as "audit" checks
 * another mod's side effect only (its hook on code the switch changes was audited): while that mod is not installed (the
 * class's package is in no module of the game) the line is satisfied; installed, it must match as any other line.
 */
public final class Guards {
    public static final String RESOURCE = "/bons_and_furious.guards.tsv";
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    private static final Map<String, String> MIXIN_KEY = new LinkedHashMap<>();
    private static final Map<String, List<String[]>> GUARD = new LinkedHashMap<>();   // key -> [class, method, desc, sha256, alternatives]
    /** Since 1.0.46: key -> the upstream builds that made the switch redundant (patches/*.json "redundant_since"). */
    private static final Map<String, List<Redundant>> REDUNDANT_SINCE = new LinkedHashMap<>();
    /** Since 1.0.46: key -> the build label whose fingerprints matched ("" = the tested build), for switches that apply. */
    private static final Map<String, String> MATCHED = new ConcurrentHashMap<>();
    /** Since 1.0.46: "key|class|method|desc" of every guard line that only audits another mod's side effect. */
    private static final Set<String> AUDIT = new java.util.HashSet<>();
    /** Since 1.0.46: what an audit line "found" when its mod is not installed: satisfied in every build label. */
    private static final String AUDITED_MOD_ABSENT = "\u0000audited mod absent";

    private record Redundant(String mod, String version, boolean evenIfMatching, String note) {}
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

    /**
     * mixinTargets (since 1.0.38): the classes a mixin class targets (its @Mixin value and targets), dotted, null for any
     * other class. declaredTargets (since 1.0.45): the same targets exactly as Mixin hands them to a config plugin's
     * shouldApplyMixin (MixinInfo.DeclaredTarget: Type.getClassName() for a class literal, the string as written for a
     * string target).
     */
    private record ClassPrints(Map<String, String> methods, String shape, List<String> mixinTargets, List<String> declaredTargets) {}

    public enum State { APPLY, DISABLED, ABSENT, MISMATCH, YIELD, REDUNDANT }

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
                // Class-shape audits have an empty method descriptor in the last column.
                String[] f = line.split("\t", -1);
                switch (f[0]) {
                    case "mixin" -> MIXIN_KEY.put(f[1], f[2]);
                    case "mod" -> MOD.put(f[1], f[2]);
                    case "guard" -> {
                        GUARD.computeIfAbsent(f[1], k -> new ArrayList<>()).add(new String[] {f[2], f[3], f[4], f[5], f.length > 6 ? f[6] : ""});
                        WANTED.computeIfAbsent(f[2], k -> new java.util.LinkedHashSet<>()).add(f[3].equals("*") ? "*" : f[3] + f[4]);
                    }
                    case "redundant" -> REDUNDANT_SINCE.computeIfAbsent(f[1], k -> new ArrayList<>())
                            .add(new Redundant(f[2], f[3], Boolean.parseBoolean(f[4]), f.length > 5 ? f[5] : ""));
                    case "audit" -> AUDIT.add(f[1] + "|" + f[2] + "|" + f[3] + "|" + f[4]);
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
            REDUNDANT_SINCE.clear();
            AUDIT.clear();
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
            case REDUNDANT -> {
                if (LOGGED.add(key + "|" + target))
                    LOGGER.info("Bons and Furious: {}: {}; {} is left unchanged", key, d.detail(), target);
            }
        }
        return false;
    }

    /**
     * Since 1.0.46: the build label whose fingerprints the applied switch matched: "" for the tested build, the label of an
     * accepted alternative build otherwise (helpers that keep an older build's code path ask this), null when the switch
     * does not apply.
     */
    public static String matchedBuild(String key) {
        load();
        return decide(key).state() == State.APPLY ? MATCHED.getOrDefault(key, "") : null;
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

    /**
     * MixinSquared's canceller: another mod's mixin is cancelled only while the switch that replaces it applies. Since
     * 1.0.45 a switch also applies when the other mod's own mixin plugin leaves that mixin out (ForeignPatches); such a
     * mixin never reaches a class, so this is not asked about it. If it reaches one anyway (the plugin answered Mixin
     * differently than it answered us), it is cancelled as before and the line says why, as a WARN.
     */
    public static boolean shouldCancel(String foreignMixin) {
        load();
        String key = CANCEL.get(foreignMixin);
        if (key == null || decide(key).state() != State.APPLY) return false;
        ForeignPatches.Yield y = YIELD_MIXIN.get(foreignMixin);
        if (y != null && ForeignPatches.listedHere(y) && LOGGED.add("cancel|" + foreignMixin)) {
            String leftOut = ForeignPatches.leftOutByPlugin(y);
            if (leftOut != null)
                LOGGER.warn("Bons and Furious: {} applies; {} {}'s {} reached a class although its mixin plugin had left it out ({}); it is left out here too, so only one copy runs",
                        key, y.name(), ForeignPatches.modVersion(y.mod()), y.shortMixin(), leftOut);
            else
                LOGGER.info("Bons and Furious: {} applies; the same change in {} {} ({}{}) is left out, so only one copy runs",
                        key, y.name(), ForeignPatches.modVersion(y.mod()), y.shortMixin(), y.option() == null ? "" : ", option " + y.option() + " off");
        }
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

    /**
     * Since 1.0.38+mc1.21.1: an injector of ours inside a vanilla method that another mod may replace whole with an
     * @Overwrite, and whose switch must then leave that mod's method alone (mixin -> method, desc; Mojang names). Mixin
     * refuses an injector into another mixin's @Overwrite unless the injector's priority is higher ("... with priority
     * 1000 cannot inject into ... merged by ... with priority 1000", thrown before require is looked at, so the server
     * stops while it starts), so such a mixin has a priority above the default and require = 0. Let in, the injector also
     * finds its call in the other mod's method; postApply therefore checks who merged the method, and when it is another
     * mod the switch's own code turns itself off at run time (the flag below) and one line names that mod. First case:
     * Sable 2.0.6 (the physics library of Create Aeronautics) replaces PlayerList.broadcast with its own loop at priority
     * 1000, which made every pack with Sable and Storage Drawers stop at the server start.
     */
    private static final Map<String, String[]> FOREIGN_OVERWRITE = Map.of(
            "bons.furious.mixin.storagedrawers_sync_c2.PlayerListCountSyncMixin", new String[] {"broadcast",
                    "(Lnet/minecraft/world/entity/player/Player;DDDDLnet/minecraft/resources/ResourceKey;Lnet/minecraft/network/protocol/Packet;)V"});

    /** True when another mod replaced PlayerList.broadcast (FOREIGN_OVERWRITE): storagedrawers_count_sync_holders sends as stock. */
    public static volatile boolean countSyncBroadcastForeign;

    /** IMixinConfigPlugin.postApply: one line when a STAND_DOWN injector found the method replaced by another mod. */
    public static void checkStandDown(String mixinClass, ClassNode target) {
        String[] f = FOREIGN_OVERWRITE.get(mixinClass);
        if (f != null) {
            checkForeignOverwrite(mixinClass, target, f);
            return;
        }
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
     * FOREIGN_OVERWRITE: postApply runs once every mod's mixins are in the class, so a method another mod replaced carries
     * that mod's @MixinMerged here. Vanilla's method (no @MixinMerged) or one of ours leaves the switch as it is.
     */
    private static void checkForeignOverwrite(String mixinClass, ClassNode target, String[] f) {
        String key = keyOf(mixinClass);
        if (key == null) return;
        for (MethodNode m : target.methods) {
            if (!m.name.equals(f[0]) || !m.desc.equals(f[1])) continue;
            String by = CallSites.mergedBy(m);
            if (by == null || by.startsWith("bons.furious.") || by.startsWith("bons.pure.") || by.startsWith("agentcraft.")) return;
            if (key.equals("storagedrawers_count_sync_holders")) countSyncBroadcastForeign = true;
            if (LOGGED.add("foreign|" + key + "|" + target.name)) {
                String mod = CallSites.modOf(by);
                LOGGER.info("Bons and Furious: {} steps aside: {}({}) replaces {}.{}; that method is left to it", key,
                        mod == null ? "" : mod + " ", by.substring(by.lastIndexOf('.') + 1), target.name.replace('/', '.'), f[0]);
            }
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
        // "mcreator" names a family, not a mod ID. Its dedicated plugins check each real mod and factory fingerprint.
        if (targetMod != null && !targetMod.equals("minecraft") && !targetMod.equals("neoforge") && !targetMod.equals("mcreator")
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
        int absent = 0, modded = 0, moddedAbsent = 0;
        List<String> problems = new ArrayList<>();
        // since 1.0.34 (as Forge 1.0.34): another mod's classes that are missing because their whole package is in no module
        // of the game (that mod is not installed); a switch that also needs such a mod is ABSENT, not MISMATCH
        List<String> uninstalled = new ArrayList<>();
        String[] found = new String[guard.size()];   // since 1.0.46: each line's fingerprint here, null = method or class absent
        for (int i = 0; i < guard.size(); i++) {
            String[] g = guard.get(i);
            boolean game = isGameClass(g[0]);
            if (!game) modded++;
            ClassPrints prints = prints(g[0]);
            if (prints == null) {
                absent++;
                if (!game) moddedAbsent++;
                if (g[3].equals(ABSENT_PRINT)) continue;   // the tested build has no such class either
                if (!game && AUDIT.contains(key + "|" + g[0] + "|" + g[1] + "|" + g[2]) && packageAbsent(g[0])) {
                    found[i] = AUDITED_MOD_ABSENT;   // the audited mod is not installed: its side effect cannot exist
                    continue;
                }
                if (!game && packageAbsent(g[0])) uninstalled.add(g[0]);

                problems.add(g[0].replace('/', '.') + " not found");
                continue;
            }
            if (g[1].equals("*")) {   // the class's set of declared methods (see Fingerprint.shape)
                found[i] = prints.shape();
                if (!Fingerprint.matches(g[3], found[i])) problems.add(g[0].replace('/', '.') + " declares other methods");

                continue;
            }
            String fp = prints.methods().get(g[1] + g[2]);
            found[i] = fp;
            if (g[3].equals(ABSENT_PRINT)) {
                if (fp != null) problems.add(g[0].replace('/', '.') + "." + g[1] + " exists");
                continue;
            }
            if (fp == null) { problems.add(g[0].replace('/', '.') + "." + g[1] + " missing"); continue; }
            if (!Fingerprint.matches(g[3], fp)) problems.add(g[0].replace('/', '.') + "." + g[1] + " differs");
        }
        // since 1.0.46: the tested build's fingerprints do not all match: one accepted alternative build may match them all
        String matched = problems.isEmpty() ? "" : alternativeBuild(guard, found);
        if (matched == null) {
            // since 1.0.28: a switch that also guards Minecraft or Forge methods it relies on is ABSENT, not MISMATCH, when every
            // class of the target mod is missing (the mod is not installed; nothing about its version is wrong)
            if (absent == guard.size() || modded > 0 && moddedAbsent == modded)
                return new Decision(State.ABSENT, "target mod " + MOD.getOrDefault(key, "?") + " is not installed");
            if (!uninstalled.isEmpty() && problems.size() == uninstalled.size()) {
                String pkg = uninstalled.get(0).substring(0, Math.max(0, uninstalled.get(0).lastIndexOf('/'))).replace('/', '.');
                return new Decision(State.ABSENT, "a mod it also needs is not installed (package " + pkg + ")");
            }
            Decision r = redundantDecision(key, false);
            if (r != null) return r;
            return new Decision(State.MISMATCH, String.join(", ", problems.subList(0, Math.min(3, problems.size())))
                    + (problems.size() > 3 ? " and " + (problems.size() - 3) + " more" : ""));

        }
        Decision r = redundantDecision(key, true);
        if (r != null) return r;
        MATCHED.put(key, matched);
        Decision c = cancelledDecision(guard);
        if (c != null) return c;
        Decision h = handlerDecision(key);
        if (h != null) return h;
        Decision y = ForeignPatches.yieldDecision(YIELDS.get(key));
        if (y != null) return y;
        return new Decision(State.APPLY, "all " + guard.size() + " fingerprints match" + (matched.isEmpty() ? "" : " (build " + matched + ")"));
    }

    /** Since 1.0.46: a guard line's fingerprint for the tested build when no alternative build is given. */
    private static final String ABSENT_PRINT = "-";

    /** Since 1.0.46: the fingerprint a guard line expects in this build label ("" = the tested build; "-" = absent). */
    private static String expectedPrint(String[] g, String label) {
        if (label.isEmpty() || g[4].isEmpty()) return g[3];
        for (String part : g[4].split(";")) {
            int eq = part.indexOf('=');
            if (eq > 0 && part.substring(0, eq).equals(label)) return part.substring(eq + 1);
        }
        return g[3];
    }

    /**
     * Since 1.0.46: the first alternative build label (in table order) whose expected fingerprints equal what every guard line
     * found here (absent = "-"), or null. One label for all lines: builds are never mixed.
     */
    private static String alternativeBuild(List<String[]> guard, String[] found) {
        List<String> labels = new ArrayList<>();
        for (String[] g : guard) {
            if (g[4].isEmpty()) continue;
            for (String part : g[4].split(";")) {
                int eq = part.indexOf('=');
                if (eq > 0 && !labels.contains(part.substring(0, eq))) labels.add(part.substring(0, eq));
            }
        }
        for (String label : labels) {
            boolean all = true;
            for (int i = 0; i < guard.size() && all; i++) {
                if (found[i] == AUDITED_MOD_ABSENT) continue;
                String want = expectedPrint(guard.get(i), label);
                all = want.equals(ABSENT_PRINT) ? found[i] == null : Fingerprint.matches(want, found[i]);
            }
            if (all) return label;
        }
        return null;
    }

    /**
     * Since 1.0.46: REDUNDANT when one of the switch's redundant_since lines names an installed mod at or above its version:
     * after a mismatch (matching = false), or with the even-if-matching flag also while the fingerprints match.
     */
    private static Decision redundantDecision(String key, boolean matching) {
        List<Redundant> since = REDUNDANT_SINCE.get(key);
        if (since == null) return null;
        for (Redundant r : since) {
            if (matching && !r.evenIfMatching()) continue;
            String installed = ForeignPatches.modVersion(r.mod());
            if (installed == null || compareVersions(installed, r.version()) < 0) continue;
            return new Decision(State.REDUNDANT, "fixed upstream in " + r.mod() + " " + r.version() + ", stands down (installed " + r.mod()
                    + " " + installed + (r.note().isEmpty() ? "" : "; " + r.note()) + ")");
        }
        return null;
    }

    /**
     * Since 1.0.46: dotted versions compared part by part on each part's leading digits ("5.27.89+mc1.20.1" reads as 5.27.89,
     * a part without leading digits ends the comparison, missing parts count as 0).
     */
    static int compareVersions(String a, String b) {
        int[] x = versionParts(a), y = versionParts(b);
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            int c = Integer.compare(i < x.length ? x[i] : 0, i < y.length ? y[i] : 0);
            if (c != 0) return c;
        }
        return 0;
    }

    private static int[] versionParts(String v) {
        List<Integer> out = new ArrayList<>();
        for (String part : v.split("[.+\\-_]")) {
            int n = 0, len = 0;
            while (len < part.length() && len < 9 && Character.isDigit(part.charAt(len))) n = n * 10 + (part.charAt(len++) - '0');
            if (len == 0) break;
            out.add(n);
        }
        return out.stream().mapToInt(Integer::intValue).toArray();
    }

    /**
     * Since 1.0.38+mc1.21.1 (as in Forge 1.0.38): a switch that changes another mod's mixin
     * (it guards that mixin class, and its MixinSquared @TargetHandler injectors work inside the handlers that mixin merges
     * into its target) steps aside when another mod's MixinSquared canceller removes that mixin. The handlers then never
     * reach the target class while the fingerprints of the mixin's class file still match, and a required injector stops the
     * game at start ("Critical injection failure ... @MixinSquared:Handler"; reported on Forge with Collections Of
     * Optimizations 4.6 cancelling ElysiumAPI's MultiNoiseBiomeSourceMixin). MixinSquared registers every canceller in its
     * config plugin's onLoad, which Mixin runs for every config before it prepares any config's mixins (where this decision
     * is made), so the answer here is the one MixinSquared applies to the target later. A mixin in our own cancel lines is
     * never asked about: our canceller would decide its switch, possibly the one decided here.
     */
    private static Decision cancelledDecision(List<String[]> guard) {
        Set<String> asked = new java.util.HashSet<>();
        for (String[] g : guard) {
            if (isGameClass(g[0]) || !asked.add(g[0])) continue;
            ClassPrints p = prints(g[0]);
            String mixin = g[0].replace('/', '.');
            if (p == null || p.mixinTargets() == null || CANCEL.containsKey(mixin)) continue;
            String canceller = cancelledBy(p.mixinTargets(), mixin);
            if (canceller == null) continue;
            String mod = CallSites.modOf(canceller), owner = CallSites.modOf(mixin);
            return new Decision(State.YIELD, (mod == null ? "" : mod + " ") + "(" + simpleName(canceller) + ") cancels "
                    + (owner == null ? "" : owner + "'s ") + simpleName(mixin) + ", the mixin this switch changes");
        }
        return null;
    }

    /** The class name of a MixinSquared canceller that removes the mixin from these targets; null when none does or MixinSquared cannot be asked. */
    private static String cancelledBy(List<String> targets, String mixin) {
        try {
            String[] by = new String[1];
            boolean cancelled = com.bawnorton.mixinsquared.canceller.MixinCancellerRegistrar.shouldCancel(targets, mixin, c -> {
                if (by[0] == null) by[0] = c;
            });
            return !cancelled ? null : by[0] != null ? by[0] : "MixinCanceller";
        } catch (Throwable t) {
            LOGGER.debug("Bons and Furious: could not ask MixinSquared whether {} is cancelled ({})", mixin, t.toString());
            return null;
        }
    }

    /** The classes a mixin class targets (dotted, as MixinSquared passes them to a canceller), or null when the class has no @Mixin. */
    private static List<String> mixinTargets(ClassNode node) {
        return targets(node, true);
    }

    /**
     * Since 1.0.45: the targets another mod's mixin class declares, exactly as Mixin passes them to its config plugin's
     * shouldApplyMixin (class literals as Type.getClassName(), strings as written), or null when the class cannot be read
     * or has no @Mixin. Read once through Mixin's bytecode provider, like every guarded class (ForeignPatches asks the
     * other mod's plugin with them).
     */
    static List<String> declaredTargets(String dottedMixin) {
        ClassPrints p = prints(dottedMixin.replace('.', '/'));
        return p == null ? null : p.declaredTargets();
    }

    /** @Mixin value + targets of a class: dotted (MixinSquared's form) or as declared (a plugin's form); null without @Mixin. */
    private static List<String> targets(ClassNode node, boolean dotted) {
        List<AnnotationNode> all = new ArrayList<>();
        if (node.invisibleAnnotations != null) all.addAll(node.invisibleAnnotations);
        if (node.visibleAnnotations != null) all.addAll(node.visibleAnnotations);
        for (AnnotationNode a : all) {
            if (!a.desc.equals("Lorg/spongepowered/asm/mixin/Mixin;")) continue;
            List<String> out = new ArrayList<>();
            for (int i = 0; a.values != null && i + 1 < a.values.size(); i += 2) {
                if (!(a.values.get(i + 1) instanceof List<?> list)) continue;   // value / targets; priority and remap are not lists
                for (Object o : list) {
                    if (o instanceof org.objectweb.asm.Type t) out.add(t.getClassName());
                    else if (o instanceof String s) out.add(dotted ? s.replace('/', '.') : s);
                }
            }
            return out;
        }
        return null;
    }

    private static String simpleName(String className) {
        return className.substring(className.lastIndexOf('.') + 1);
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
            p = new ClassPrints(methods, shape, mixinTargets(node), targets(node, false));
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
