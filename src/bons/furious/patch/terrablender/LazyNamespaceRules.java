package bons.furious.patch.terrablender;

import com.mojang.logging.LogUtils;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.SurfaceRules;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.transformer.meta.MixinMerged;

/**
 * Bons and Furious switch terrablender_lazy_namespace_rules (TerraBlender 3.0.1.10, LGPL-3.0; Minecraft 1.20.1, server
 * side of world generation). SRG names for Minecraft members. Extends SurfaceRules only to name its protected nested
 * types; never instantiated.
 *
 * What it costs. NamespacedSurfaceRuleSource.apply runs once per chunk surface pass and builds the rule tree of EVERY
 * namespace TerraBlender knows (minecraft's default rules plus every mod that registered surface rules: nine in this
 * pack), each hundreds of SequenceRule / TestRule / condition objects, although a chunk normally reaches one or two
 * namespaces (review-8 JFR: 4.5-25% of all C2ME worker allocation sits under this method).
 *
 * What the switch does. TerraBlender's apply hands each namespace's source to a lambda that calls source.apply(context)
 * and puts the result in its map. NamespacedSourceDeferMixin gives that lambda, for a namespace whose source tree is made
 * only of vanilla rule and condition sources, a DeferredSource instead: its apply(context) returns a DeferredRule that
 * makes the very same source.apply(context) call on the rule's first tryApply. A namespace the chunk never reaches is
 * never built. A tree with any other type (another mod's rule source), or vanilla source classes carrying an unverified
 * mixin, is built up front exactly as before.
 *
 * Why the result is identical. Vanilla sources' apply only reads what is fixed for the whole context (its
 * WorldGenerationContext, RandomState and SurfaceSystem) and allocates new rule and condition objects; the two shared
 * caches it touches (RandomState.getOrCreateNoise / getOrCreateRandomFactory) return the same value whenever they are
 * first asked; the context's preallocated conditions (hole, steep, temperature, above preliminary surface) are the same
 * objects either way. The one construction-time value is LazyCondition.lastUpdate = the context's counter - 1, which only
 * forces the first test to compute: a condition built at its first use and one built up front but not tested before that
 * use compute on the same test and cache identically afterwards (ModernFix 5.27.77 makes the Y conditions compute on
 * every test anyway). Nothing reads a namespace's rules before its first use. TerraBlender's map keys are unchanged, so
 * NamespacedRule selects the same namespace.
 *
 * -Dbons_and_furious.lazyNamespaceRules=false builds every namespace up front (checked per surface pass).
 * -Dbons_and_furious.lazyNamespaceRules.shadow=true (verification runs only) also builds every deferred namespace up front
 * (the original timing) and, at each tryApply of the late-built rule, asks the up-front twin too and counts different
 * answers (SHADOW_CHECKS / SHADOW_MISMATCHES); the late-built rule's answer is the one returned.
 */
public final class LazyNamespaceRules extends SurfaceRules {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.lazyNamespaceRules", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.lazyNamespaceRules.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    /** Namespace rules deferred / built at first use / built up front (diagnostics; the proof reads them). */
    public static final AtomicLong DEFERRED = new AtomicLong(), BUILT_LATE = new AtomicLong(), EAGER = new AtomicLong();
    private static volatile boolean announced;

    private static final String P = "net.minecraft.world.level.levelgen.SurfaceRules$";
    /** Vanilla sources whose children are other sources (walked). */
    private static final Set<String> INNER = Set.of(P + "SequenceRuleSource", P + "TestRuleSource", P + "NotConditionSource");
    /** Vanilla sources with no source children. */
    private static final Set<String> LEAVES = Set.of(P + "BlockRuleSource", P + "Bandlands", P + "BiomeConditionSource",
            P + "NoiseThresholdConditionSource", P + "VerticalGradientConditionSource", P + "YConditionSource", P + "WaterConditionSource",
            P + "StoneDepthCheck", P + "Hole", P + "Steep", P + "Temperature", P + "AbovePreliminarySurface");
    /** The rule and condition classes those sources create (a mixin on them could keep construction-time state). */
    private static final Set<String> PRODUCTS = Set.of(P + "BiomeConditionSource$1BiomeCondition", P + "NoiseThresholdConditionSource$1NoiseThresholdCondition",
            P + "VerticalGradientConditionSource$1VerticalGradientCondition", P + "YConditionSource$1YCondition", P + "WaterConditionSource$1WaterCondition",
            P + "StoneDepthCheck$1StoneDepthCondition", P + "LazyCondition", P + "LazyXZCondition", P + "LazyYCondition", P + "TestRule", P + "SequenceRule",
            P + "NotCondition", P + "StateRule");
    /** Other mods' mixins on those classes whose code was read (ModernFix 5.27.77), by SHA-256 of the class file. */
    private static final Map<String, String> VERIFIED = Map.of(
            "org.embeddedt.modernfix.common.mixin.perf.optimize_surface_rules.BiomeConditionSourceMixin", "5039f42b60d3342920760615f025b7e68fa24999b0937dd361f9c756f010450e",
            "org.embeddedt.modernfix.common.mixin.perf.worldgen_allocation.SurfaceRulesMixin", "6a5498d4867f82a07e984b13b025a9f2f1eac870ddaba42a0941939d0e033488",
            "org.embeddedt.modernfix.common.mixin.perf.worldgen_allocation.SequenceRuleMixin", "8442d76ffd8db7cdef78e693f5741dc8c6432b561f4b44ed57663f3670bd3bad");

    private static volatile Boolean classesPure;
    private static final Map<Object, Boolean> VERDICTS = new IdentityHashMap<>();

    private LazyNamespaceRules() {
    }

    /** The entry TerraBlender's apply lambda gets: the same key with a deferring source, or the original entry. */
    public static Map.Entry<String, ?> defer(Map.Entry<String, ?> entry) {
        Object source = entry.getValue();
        if (!enabled || !(source instanceof SurfaceRules.RuleSource rule) || !deferrable(rule)) {
            EAGER.incrementAndGet();
            return entry;
        }
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: terrablender_lazy_namespace_rules applies (a TerraBlender namespace's surface rules are built when the chunk first reaches that namespace){}",
                    SHADOW ? " - shadow verification on" : "");
        }
        DEFERRED.incrementAndGet();
        return Map.entry(entry.getKey(), new DeferredSource(rule));
    }

    /** The rule to call for a selected namespace rule: a deferred one is built now (at its first use) and returned. */
    public static Object resolve(Object selected) {
        return selected instanceof DeferredRule d ? d.built() : selected;
    }

    /** True when the source tree is made only of vanilla sources and no unverified mixin changes them. Public for the proof. */
    public static boolean deferrable(SurfaceRules.RuleSource source) {
        if (!classesPure()) return false;
        synchronized (VERDICTS) {
            Boolean known = VERDICTS.get(source);
            if (known != null) return known;
        }
        boolean verdict;
        try {
            verdict = walk(source, new IdentityHashMap<>(), 0);
        } catch (Throwable t) {
            verdict = false;
        }
        synchronized (VERDICTS) {
            VERDICTS.put(source, verdict);
        }
        return verdict;
    }

    private static boolean walk(Object node, IdentityHashMap<Object, Boolean> seen, int depth) throws IllegalAccessException {
        if (node == null || depth > 512) return false;
        if (seen.put(node, Boolean.TRUE) != null) return true;
        String name = node.getClass().getName();
        if (LEAVES.contains(name)) return true;
        if (!INNER.contains(name)) return false;
        for (Field f : node.getClass().getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers())) continue;
            f.setAccessible(true);
            Object v = f.get(node);
            if (v instanceof SurfaceRules.RuleSource || v instanceof SurfaceRules.ConditionSource) {
                if (!walk(v, seen, depth + 1)) return false;
            } else if (v instanceof List<?> list) {
                for (Object e : list) if (!walk(e, seen, depth + 1)) return false;
            }
        }
        return true;
    }

    /** Once per run: every mixin merged into the vanilla source, rule and condition classes is a verified one (or none). */
    static boolean classesPure() {
        Boolean known = classesPure;
        if (known != null) return known;
        synchronized (LazyNamespaceRules.class) {
            if (classesPure != null) return classesPure;
            String problem = null;
            try {
                ClassLoader loader = SurfaceRules.class.getClassLoader();
                List<String> types = new ArrayList<>(INNER);
                types.addAll(LEAVES);
                types.addAll(PRODUCTS);
                outer:
                for (String type : types) {
                    for (Method m : Class.forName(type, false, loader).getDeclaredMethods()) {
                        MixinMerged merged = m.getAnnotation(MixinMerged.class);
                        if (merged == null || merged.mixin().startsWith("bons.furious.mixin.")) continue;
                        String want = VERIFIED.get(merged.mixin());
                        if (want == null || !want.equals(sha256(loader, merged.mixin()))) {
                            problem = type.substring(P.length()) + " carries code from " + merged.mixin() + (want == null ? ", which this switch has not verified" : ", not the verified build");
                            break outer;
                        }
                    }
                }
            } catch (Throwable t) {
                problem = "the check failed (" + t + ")";
            }
            classesPure = problem == null;
            if (problem != null)
                LOGGER.info("Bons and Furious: terrablender_lazy_namespace_rules stands down: {}; every namespace's surface rules are built up front", problem);
            return classesPure;
        }
    }

    private static String sha256(ClassLoader loader, String className) throws Exception {
        try (InputStream in = loader.getResourceAsStream(className.replace('.', '/') + ".class")) {
            if (in == null) return "unreadable";
            byte[] d = MessageDigest.getInstance("SHA-256").digest(in.readAllBytes());
            StringBuilder sb = new StringBuilder(64);
            for (byte b : d) sb.append(Character.forDigit((b >> 4) & 15, 16)).append(Character.forDigit(b & 15, 16));
            return sb.toString();
        }
    }

    /** TerraBlender's map value for a deferred namespace: applying it to the context only records the context. */
    static final class DeferredSource implements SurfaceRules.RuleSource {
        final SurfaceRules.RuleSource source;

        DeferredSource(SurfaceRules.RuleSource source) {
            this.source = source;
        }

        @Override
        public KeyDispatchDataCodec<? extends SurfaceRules.RuleSource> m_213795_() {
            return this.source.m_213795_();
        }

        @Override
        public SurfaceRules.SurfaceRule apply(SurfaceRules.Context context) {
            DeferredRule rule = new DeferredRule(this.source, context);
            if (SHADOW) rule.twin = this.source.apply(context);
            return rule;
        }
    }

    /** The namespace rule, built by source.apply(context) at its first use. One thread (one surface context). */
    public static final class DeferredRule implements SurfaceRules.SurfaceRule {
        private final SurfaceRules.RuleSource source;
        private final SurfaceRules.Context context;
        private SurfaceRules.SurfaceRule built;
        SurfaceRules.SurfaceRule twin;

        DeferredRule(SurfaceRules.RuleSource source, SurfaceRules.Context context) {
            this.source = source;
            this.context = context;
        }

        SurfaceRules.SurfaceRule built() {
            SurfaceRules.SurfaceRule r = this.built;
            if (r == null) {
                r = this.source.apply(this.context);
                BUILT_LATE.incrementAndGet();
                if (SHADOW) r = new Shadowed(r, this.twin);
                this.built = r;
            }
            return r;
        }

        @Override
        public BlockState m_183550_(int x, int y, int z) {
            return this.built().m_183550_(x, y, z);
        }
    }

    /** Shadow mode: answers with the late-built rule and compares with the rule built up front. */
    static final class Shadowed implements SurfaceRules.SurfaceRule {
        final SurfaceRules.SurfaceRule late, early;

        Shadowed(SurfaceRules.SurfaceRule late, SurfaceRules.SurfaceRule early) {
            this.late = late;
            this.early = early;
        }

        @Override
        public BlockState m_183550_(int x, int y, int z) {
            BlockState a = this.late.m_183550_(x, y, z);
            BlockState b = this.early.m_183550_(x, y, z);
            SHADOW_CHECKS.incrementAndGet();
            if (a != b && SHADOW_MISMATCHES.incrementAndGet() <= 20)
                LOGGER.warn("Bons and Furious: terrablender_lazy_namespace_rules shadow mismatch at {},{},{}: {} vs {}", x, y, z, a, b);
            return a;
        }
    }
}
