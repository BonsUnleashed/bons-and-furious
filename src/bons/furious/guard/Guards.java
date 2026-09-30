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
    private static final Map<String, Decision> DECISIONS = new ConcurrentHashMap<>();
    private static final Set<String> LOGGED = ConcurrentHashMap.newKeySet();
    private static volatile boolean loaded;

    public enum State { APPLY, DISABLED, ABSENT, MISMATCH }

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
                    default -> LOGGER.warn("Bons and Furious: unknown line in {}: {}", RESOURCE, line);
                }
            }
        } catch (Exception e) {
            LOGGER.error("Bons and Furious could not read {}; no patch will be applied", RESOURCE, e);
            MIXIN_KEY.clear();
            GUARD.clear();
            CANCEL.clear();
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
        }
        return false;
    }

    /** MixinSquared's canceller: another mod's mixin is cancelled only while the switch that replaces it applies. */
    public static boolean shouldCancel(String foreignMixin) {
        load();
        String key = CANCEL.get(foreignMixin);
        return key != null && decide(key).state() == State.APPLY;
    }

    public static Decision decide(String key) {
        return DECISIONS.computeIfAbsent(key, Guards::compute);
    }

    private static Decision compute(String key) {
        if (!PureConfig.isEnabled(key)) return new Decision(State.DISABLED, "disabled by config");
        List<String[]> guard = GUARD.getOrDefault(key, List.of());
        if (guard.isEmpty()) return new Decision(State.MISMATCH, "no fingerprints recorded for " + key);
        int absent = 0;
        List<String> problems = new ArrayList<>();
        Map<String, ClassNode> nodes = new LinkedHashMap<>();
        for (String[] g : guard) {
            ClassNode node = nodes.computeIfAbsent(g[0], Guards::read);
            if (node == null) { absent++; problems.add(g[0].replace('/', '.') + " not found"); continue; }
            if (g[1].equals("*")) {   // the class's set of declared methods (see Fingerprint.shape)
                if (!Fingerprint.shape(node).equals(g[3])) problems.add(g[0].replace('/', '.') + " declares other methods");
                continue;
            }
            MethodNode m = null;
            for (MethodNode x : node.methods) if (x.name.equals(g[1]) && x.desc.equals(g[2])) { m = x; break; }
            if (m == null) { problems.add(g[0].replace('/', '.') + "." + g[1] + " missing"); continue; }
            String fp = Fingerprint.of(m);
            if (!fp.equals(g[3])) problems.add(g[0].replace('/', '.') + "." + g[1] + " differs");
        }
        if (absent == guard.size()) return new Decision(State.ABSENT, "target mod " + MOD.getOrDefault(key, "?") + " is not installed");
        if (!problems.isEmpty()) return new Decision(State.MISMATCH, String.join(", ", problems.subList(0, Math.min(3, problems.size())))
                + (problems.size() > 3 ? " and " + (problems.size() - 3) + " more" : ""));
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
