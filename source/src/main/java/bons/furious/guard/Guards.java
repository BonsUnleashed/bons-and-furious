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
import org.objectweb.asm.tree.ClassNode;
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
                    case "guard" -> GUARD.computeIfAbsent(f[1], k -> new ArrayList<>()).add(new String[] {f[2], f[3], f[4], f[5]});
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
            Decision y = ForeignPatches.yieldDecision(YIELDS.get(key));
            return y != null ? y : new Decision(State.APPLY, "switched on");
        }
        int absent = 0;
        List<String> problems = new ArrayList<>();
        Map<String, ClassNode> nodes = new LinkedHashMap<>();
        for (String[] g : guard) {
            ClassNode node = nodes.computeIfAbsent(g[0], Guards::read);
            if (node == null) { absent++; problems.add(g[0].replace('/', '.') + " not found"); continue; }
            if (g[1].equals("*")) {   // the class's set of declared methods (see Fingerprint.shape)
                if (!Fingerprint.matches(g[3], Fingerprint.shape(node))) problems.add(g[0].replace('/', '.') + " declares other methods");
                continue;
            }
            MethodNode m = null;
            for (MethodNode x : node.methods) if (x.name.equals(g[1]) && x.desc.equals(g[2])) { m = x; break; }
            if (m == null) { problems.add(g[0].replace('/', '.') + "." + g[1] + " missing"); continue; }
            String fp = Fingerprint.of(m);
            if (!Fingerprint.matches(g[3], fp)) problems.add(g[0].replace('/', '.') + "." + g[1] + " differs");
        }
        if (absent == guard.size()) return new Decision(State.ABSENT, "target mod " + MOD.getOrDefault(key, "?") + " is not installed");
        if (!problems.isEmpty()) return new Decision(State.MISMATCH, String.join(", ", problems.subList(0, Math.min(3, problems.size())))
                + (problems.size() > 3 ? " and " + (problems.size() - 3) + " more" : ""));
        Decision y = ForeignPatches.yieldDecision(YIELDS.get(key));
        if (y != null) return y;
        return new Decision(State.APPLY, "all " + guard.size() + " fingerprints match");
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
