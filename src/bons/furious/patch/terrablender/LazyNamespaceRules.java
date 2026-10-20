package bons.furious.patch.terrablender;

import bons.furious.guard.Fingerprint;
import com.google.common.collect.MapMaker;
import com.mojang.logging.LogUtils;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.util.random.WeightedEntry;
import net.minecraft.util.random.WeightedRandomList;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.SurfaceRules;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.transformer.meta.MixinMerged;

/**
 * Bons and Furious switch terrablender_lazy_namespace_rules (TerraBlender 3.0.1.10 and 3.0.1.11, LGPL-3.0; Minecraft
 * 1.20.1, server side of world generation). SRG names for Minecraft members. Extends SurfaceRules only to name its
 * protected nested types; never instantiated.
 *
 * What it costs. NamespacedSurfaceRuleSource.apply runs once per chunk surface pass and builds the rule tree of EVERY
 * namespace TerraBlender knows (minecraft's default rules plus every mod that registered surface rules: nine in this
 * pack), each hundreds of SequenceRule / TestRule / condition objects, although a chunk normally reaches one or two
 * namespaces (review-8 JFR: 4.5-25% of all C2ME worker allocation sits under this method).
 *
 * What the switch does. TerraBlender's apply hands each namespace's source to a lambda that calls source.apply(context)
 * and puts the result in its map. NamespacedSourceDeferMixin gives that lambda, for a namespace whose source tree is made
 * only of sources this class has verified, a DeferredSource instead: its apply(context) returns a DeferredRule that makes
 * the very same source.apply(context) call on the rule's first tryApply. A namespace the chunk never reaches is never
 * built. A tree with any other type, or a verified type carrying unverified code, is built up front exactly as before.
 *
 * Verified sources: the vanilla rule and condition sources (their classes may carry only the mixins listed in VERIFIED,
 * read by SHA-256 of the mixin class file), and since 1.0.45 Oh The Biomes We've Gone 1.8.0's three rule types
 * (FOREIGN_SOURCES, by SHA-256 of their own class files, with no other mod's code merged into them).
 *
 * Why the result is identical. A verified source's apply only reads what is fixed for the whole context (its
 * WorldGenerationContext, RandomState and SurfaceSystem) and allocates new rule and condition objects; the two shared
 * caches it touches (RandomState.getOrCreateNoise / getOrCreateRandomFactory) return the same value whenever they are
 * first asked; the context's preallocated conditions (hole, steep, temperature, above preliminary surface) are the same
 * objects either way. The one construction-time value is LazyCondition.lastUpdate = the context's counter - 1, which only
 * forces the first test to compute: a condition built at its first use and one built up front but not tested before that
 * use compute on the same test and cache identically afterwards (ModernFix 5.27.77 makes the Y conditions compute on
 * every test anyway). Nothing reads a namespace's rules before its first use. TerraBlender's map keys are unchanged, so
 * NamespacedRule selects the same namespace.
 * - C2ME 0.2.0+alpha.12 (MIT): MixinMaterialRulesSequenceMaterialRule copies SequenceRuleSource's immutable list into an
 *   array when the source is constructed (once, at load) and its apply builds the same rules in the same order (a
 *   one-element sequence returns its rule unwrapped); MixinMaterialRulesSequenceBlockStateRule copies SequenceRule's list
 *   into an array at construction and walks it in order. Neither keeps anything from a context.
 * - Oh The Biomes We've Gone 1.8.0 (read, nothing carried): WeightedRuleSource.apply picks one sub-source per local (x, z)
 *   from context.system.noiseRandom.at(x, 0, z) (a fresh positional random: the same pick at any time) and applies it;
 *   BetweenRepeatingNoiseRange.apply delegates to the vanilla sequence it built in its constructor; BandsRuleSource.apply
 *   returns a rule that reads the SurfaceSystem's band table only when asked (tryApply time, unchanged).
 * - TerraBlender: NamespacedRule.tryApply calls tryApply on the selected namespace rule only (3.0.1.10 selects per call;
 *   3.0.1.11 keeps the last biome's rule in a field, which then holds the DeferredRule); its code is checked at the first
 *   surface pass against both tested builds (TESTED_RULE_BUILDS, guard fingerprints of the class file in TerraBlender's
 *   jar), because the guard table holds one fingerprint per method.
 *
 * -Dbons_and_furious.lazyNamespaceRules=false builds every namespace up front (checked per surface pass).
 * -Dbons_and_furious.lazyNamespaceRules.shadow=true (verification runs only) also builds every deferred namespace up front
 * (the original timing) and, at each tryApply of the late-built rule, asks the up-front twin too and counts different
 * answers (SHADOW_CHECKS / SHADOW_MISMATCHES, SHADOW_CHECKS_FOREIGN for trees with a verified foreign type); the late-built
 * rule's answer is the one returned.
 */
public final class LazyNamespaceRules extends SurfaceRules {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.lazyNamespaceRules", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.lazyNamespaceRules.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong(), SHADOW_CHECKS_FOREIGN = new AtomicLong();
    /** Namespace rules deferred / built at first use / built up front (diagnostics; the proof reads them). */
    public static final AtomicLong DEFERRED = new AtomicLong(), BUILT_LATE = new AtomicLong(), EAGER = new AtomicLong();
    /** 1.0.45: deferred namespace rules whose tree holds a verified foreign type (Oh The Biomes We've Gone's). */
    public static final AtomicLong DEFERRED_FOREIGN = new AtomicLong();
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
    /**
     * Other mods' mixins on those classes whose code was read, by SHA-256 of the class file: ModernFix 5.27.77 (1.0.28) and,
     * since 1.0.45, C2ME 0.2.0+alpha.12's two surface-builder mixins (nested module c2meF-opts-allocs).
     */
    private static final Map<String, String> VERIFIED = Map.of(
            "org.embeddedt.modernfix.common.mixin.perf.optimize_surface_rules.BiomeConditionSourceMixin", "5039f42b60d3342920760615f025b7e68fa24999b0937dd361f9c756f010450e",
            "org.embeddedt.modernfix.common.mixin.perf.worldgen_allocation.SurfaceRulesMixin", "6a5498d4867f82a07e984b13b025a9f2f1eac870ddaba42a0941939d0e033488",
            "org.embeddedt.modernfix.common.mixin.perf.worldgen_allocation.SequenceRuleMixin", "8442d76ffd8db7cdef78e693f5741dc8c6432b561f4b44ed57663f3670bd3bad",
            "com.ishland.c2me.opts.allocs.mixin.surfacebuilder.MixinMaterialRulesSequenceMaterialRule", "b2b8273fb29e24e97ea1dc4bfd1d8d664aa1a6c988ae6773cb8f68f81ca5548f",
            "com.ishland.c2me.opts.allocs.mixin.surfacebuilder.MixinMaterialRulesSequenceBlockStateRule", "808dcca80bdf8188c61f0aa899d5dda4d28cc823b76cf9823a70515e2319283f");

    private static final String BWG = "net.potionstudios.biomeswevegone.world.level.levelgen.surfacerules.";
    private static final String BWG_WEIGHTED = BWG + "WeightedRuleSource", BWG_BETWEEN = BWG + "BetweenRepeatingNoiseRange", BWG_BANDS = BWG + "BandsRuleSource";
    /** 1.0.45: other mods' rule sources whose code was read, by SHA-256 of their class file (Oh The Biomes We've Gone 1.8.0). */
    private static final Map<String, String> FOREIGN_SOURCES = Map.of(
            BWG_WEIGHTED, "76985e13e82c914b329be5e5f5931b6e431e9e9a473bc793228675d0cc719601",
            BWG_BETWEEN, "f29a5b038442aaa2b339b5c2dec465f16797078bdabef8b5b6f989d063c34b89",
            BWG_BANDS, "1f8b0e0484120716287dc95fe8c3bcaaca81b105464e753e46b01af2f4af5851");
    /** Verdict per foreign source class (verified build, no other mod's code merged); logged once when false. */
    private static final ConcurrentMap<Class<?>, Boolean> FOREIGN_VERDICTS = new ConcurrentHashMap<>();

    /**
     * 1.0.45: the TerraBlender builds whose NamespacedRule.tryApply this switch was tested against: every method tryApply
     * consists of (name + descriptor -> guard fingerprint, bons.furious.guard.Fingerprint, of the class file in
     * TerraBlender's jar; computed by tools/mixedproof/TerrablenderFingerprints.py). One build must match in full.
     */
    private static final String TRY_APPLY = "m_183550_(III)Lnet/minecraft/world/level/block/state/BlockState;";
    private static final Map<String, Map<String, String>> TESTED_RULE_BUILDS = Map.of(
            "3.0.1.10", Map.of(TRY_APPLY, "e6ac2f4acecc7a669b4786c4426d1a945f84daad3b1e68aed2ecba758cbabbd0",
                    "lambda$tryApply$0(Lnet/minecraft/resources/ResourceKey;)Z", "1dada2146fd6a9af80c8433a448644e76d0c1aebaf5357a4b229fe263ed210a3"),
            "3.0.1.11", Map.of(TRY_APPLY, "4958c2d17103c3a5df286e3f62974c6521c9fdc35485c6536a3d80c0afc47cad",
                    "lambda$tryApply$0(Lnet/minecraft/resources/ResourceKey;)Lnet/minecraft/world/level/levelgen/SurfaceRules$SurfaceRule;",
                    "2912a560840f70dba68a822d96c22e685dc4f3c1f9bfa0c58f60abf3500a117e",
                    "lambda$tryApply$1(Lnet/minecraft/resources/ResourceKey;)Z", "1dada2146fd6a9af80c8433a448644e76d0c1aebaf5357a4b229fe263ed210a3"));
    private static final String NAMESPACED_RULE = "terrablender/worldgen/surface/NamespacedSurfaceRuleSource$NamespacedRule";
    /** The tested TerraBlender build found at the first surface pass (diagnostics; the proof reads it). */
    public static volatile String terraBlenderBuild;

    private static volatile Boolean classesPure;
    /**
     * 1.0.34: weak keys compared by identity (MapMaker.weakKeys), concurrent. TerraBlender builds a new "minecraft" source
     * tree for every world load (TBSurfaceRuleData per NoiseGeneratorSettings), and a strong map kept every one of them.
     * 1.0.45: the value is the verdict: NOT (built up front), VANILLA (deferrable) or FOREIGN (deferrable, holds a verified
     * foreign type).
     */
    private static final ConcurrentMap<Object, Integer> VERDICTS = new MapMaker().weakKeys().makeMap();
    private static final int NOT = 0, VANILLA = 1, FOREIGN = 2;

    private LazyNamespaceRules() {
    }

    /** The entry TerraBlender's apply lambda gets: the same key with a deferring source, or the original entry. */
    public static Map.Entry<String, ?> defer(Map.Entry<String, ?> entry) {
        Object source = entry.getValue();
        int verdict = enabled && source instanceof SurfaceRules.RuleSource rule ? verdict(rule) : NOT;
        if (verdict == NOT) {
            EAGER.incrementAndGet();
            return entry;
        }
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: terrablender_lazy_namespace_rules applies (a TerraBlender namespace's surface rules are built when the chunk first reaches that namespace; TerraBlender {}){}",
                    terraBlenderBuild, SHADOW ? " - shadow verification on" : "");
        }
        DEFERRED.incrementAndGet();
        if (verdict == FOREIGN) DEFERRED_FOREIGN.incrementAndGet();
        return Map.entry(entry.getKey(), new DeferredSource((SurfaceRules.RuleSource) source, verdict == FOREIGN));
    }

    /** The rule to call for a selected namespace rule: a deferred one is built now (at its first use) and returned. */
    public static Object resolve(Object selected) {
        return selected instanceof DeferredRule d ? d.built() : selected;
    }

    /** True when the source tree is made only of verified sources and no unverified mixin changes them. Public for the proof. */
    public static boolean deferrable(SurfaceRules.RuleSource source) {
        return verdict(source) != NOT;
    }

    private static int verdict(SurfaceRules.RuleSource source) {
        if (!classesPure()) return NOT;
        Integer known = VERDICTS.get(source);   // 1.0.34: concurrent map, no lock (was synchronized on an IdentityHashMap)
        if (known != null) return known;
        int verdict;
        try {
            boolean[] foreign = new boolean[1];
            verdict = walk(source, new IdentityHashMap<>(), 0, foreign) ? (foreign[0] ? FOREIGN : VANILLA) : NOT;
        } catch (Throwable t) {
            verdict = NOT;
        }
        VERDICTS.put(source, verdict);
        return verdict;
    }

    private static boolean walk(Object node, IdentityHashMap<Object, Boolean> seen, int depth, boolean[] foreign) throws ReflectiveOperationException {
        if (node == null || depth > 512) return false;
        if (seen.put(node, Boolean.TRUE) != null) return true;
        Class<?> type = node.getClass();
        String name = type.getName();
        if (LEAVES.contains(name)) return true;
        if (INNER.contains(name)) {
            for (Field f : type.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers())) continue;
                f.setAccessible(true);
                Object v = f.get(node);
                if (v instanceof SurfaceRules.RuleSource || v instanceof SurfaceRules.ConditionSource) {
                    if (!walk(v, seen, depth + 1, foreign)) return false;
                } else if (v instanceof List<?> list) {
                    for (Object e : list) if (!walk(e, seen, depth + 1, foreign)) return false;
                }
            }
            return true;
        }
        // 1.0.45: Oh The Biomes We've Gone 1.8.0's rule types, each only when its class file is the one that was read
        if (!FOREIGN_SOURCES.containsKey(name) || !foreignVerified(type)) return false;
        foreign[0] = true;
        if (name.equals(BWG_BANDS)) return true;                               // leaf: no source children
        if (name.equals(BWG_BETWEEN)) {                                         // apply delegates to this one vanilla sequence
            Field rule = type.getDeclaredField("rule");
            rule.setAccessible(true);
            return walk(rule.get(node), seen, depth + 1, foreign);
        }
        Field list = type.getDeclaredField("ruleSources");                      // WeightedRuleSource: every weighted entry
        list.setAccessible(true);
        Object weighted = list.get(node);
        if (!(weighted instanceof WeightedRandomList<?> entries)) return false;
        for (Object e : entries.m_146338_()) {
            if (!(e instanceof WeightedEntry.Wrapper<?> w) || !walk(w.m_146310_(), seen, depth + 1, foreign)) return false;
        }
        return true;
    }

    /** A foreign source class is used only when its class file is the tested one and no other mod's code is merged into it. */
    private static boolean foreignVerified(Class<?> type) {
        Boolean known = FOREIGN_VERDICTS.get(type);
        if (known != null) return known;
        String problem = null;
        try {
            if (!FOREIGN_SOURCES.get(type.getName()).equals(sha256(type.getClassLoader(), type.getName()))) problem = "not the tested build";
            else {
                for (Method m : type.getDeclaredMethods()) {
                    MixinMerged merged = m.getAnnotation(MixinMerged.class);
                    if (merged != null && !merged.mixin().startsWith("bons.furious.mixin.")) {
                        problem = "carries code from " + merged.mixin();
                        break;
                    }
                }
            }
        } catch (Throwable t) {
            problem = "the check failed (" + t + ")";
        }
        boolean ok = problem == null;
        if (FOREIGN_VERDICTS.putIfAbsent(type, ok) == null && !ok)
            LOGGER.info("Bons and Furious: terrablender_lazy_namespace_rules: {} is {}; namespaces that use it are built up front", type.getName(), problem);
        return FOREIGN_VERDICTS.get(type);
    }

    /**
     * Once per run: TerraBlender's NamespacedRule is a tested build, and every mixin merged into the vanilla source, rule and
     * condition classes is a verified one (or none).
     */
    static boolean classesPure() {
        Boolean known = classesPure;
        if (known != null) return known;
        synchronized (LazyNamespaceRules.class) {
            if (classesPure != null) return classesPure;
            String problem;
            try {
                String build = terraBlenderBuild();
                terraBlenderBuild = build;
                problem = build == null ? "TerraBlender's NamespacedRule.tryApply is not a tested build (3.0.1.10 or 3.0.1.11)" : mixinProblem();
            } catch (Throwable t) {
                problem = "the check failed (" + t + ")";
            }
            classesPure = problem == null;
            if (problem != null)
                LOGGER.info("Bons and Furious: terrablender_lazy_namespace_rules stands down: {}; every namespace's surface rules are built up front", problem);
            return classesPure;
        }
    }

    /** Null when every mixin merged into the vanilla source, rule and condition classes is a verified one; else why not. Public for the proof. */
    public static String mixinProblem() throws Exception {
        ClassLoader loader = SurfaceRules.class.getClassLoader();
        List<String> types = new ArrayList<>(INNER);
        types.addAll(LEAVES);
        types.addAll(PRODUCTS);
        for (String type : types) {
            for (Method m : Class.forName(type, false, loader).getDeclaredMethods()) {
                MixinMerged merged = m.getAnnotation(MixinMerged.class);
                if (merged == null || merged.mixin().startsWith("bons.furious.mixin.")) continue;
                String want = VERIFIED.get(merged.mixin());
                if (want == null || !want.equals(sha256(loader, merged.mixin())))
                    return type.substring(P.length()) + " carries code from " + merged.mixin() + (want == null ? ", which this switch has not verified" : ", not the verified build");
            }
        }
        return null;
    }

    /**
     * The tested TerraBlender build whose NamespacedRule code is installed (TESTED_RULE_BUILDS), or null. Reads the class file
     * from TerraBlender's jar (as the guards read classes before any mixin) and fingerprints its methods.
     */
    static String terraBlenderBuild() throws Exception {
        ClassLoader tb = Class.forName(NAMESPACED_RULE.replace('/', '.').replace("$NamespacedRule", ""), false, LazyNamespaceRules.class.getClassLoader()).getClassLoader();
        try (InputStream in = tb.getResourceAsStream(NAMESPACED_RULE + ".class")) {
            return in == null ? null : testedBuild(in.readAllBytes());
        }
    }

    /** The tested build a NamespacedRule class file is (its tryApply methods' guard fingerprints), or null. Public for the proof. */
    public static String testedBuild(byte[] namespacedRuleClassFile) {
        ClassNode node = new ClassNode();
        new ClassReader(namespacedRuleClassFile).accept(node, 0);
        Map<String, String> prints = new HashMap<>();
        for (MethodNode m : node.methods) prints.put(m.name + m.desc, Fingerprint.of(m));
        for (Map.Entry<String, Map<String, String>> build : TESTED_RULE_BUILDS.entrySet()) {
            boolean all = true;
            for (Map.Entry<String, String> method : build.getValue().entrySet()) all &= method.getValue().equals(prints.get(method.getKey()));
            if (all) return build.getKey();
        }
        return null;
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
        final boolean foreign;

        DeferredSource(SurfaceRules.RuleSource source, boolean foreign) {
            this.source = source;
            this.foreign = foreign;
        }

        @Override
        public KeyDispatchDataCodec<? extends SurfaceRules.RuleSource> m_213795_() {
            return this.source.m_213795_();
        }

        @Override
        public SurfaceRules.SurfaceRule apply(SurfaceRules.Context context) {
            DeferredRule rule = new DeferredRule(this.source, context, this.foreign);
            if (SHADOW) rule.twin = this.source.apply(context);
            return rule;
        }
    }

    /** The namespace rule, built by source.apply(context) at its first use. One thread (one surface context). */
    public static final class DeferredRule implements SurfaceRules.SurfaceRule {
        private final SurfaceRules.RuleSource source;
        private final SurfaceRules.Context context;
        private final boolean foreign;
        private SurfaceRules.SurfaceRule built;
        SurfaceRules.SurfaceRule twin;

        DeferredRule(SurfaceRules.RuleSource source, SurfaceRules.Context context, boolean foreign) {
            this.source = source;
            this.context = context;
            this.foreign = foreign;
        }

        SurfaceRules.SurfaceRule built() {
            SurfaceRules.SurfaceRule r = this.built;
            if (r == null) {
                r = this.source.apply(this.context);
                BUILT_LATE.incrementAndGet();
                if (SHADOW) r = new Shadowed(r, this.twin, this.foreign);
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
        final boolean foreign;

        Shadowed(SurfaceRules.SurfaceRule late, SurfaceRules.SurfaceRule early, boolean foreign) {
            this.late = late;
            this.early = early;
            this.foreign = foreign;
        }

        @Override
        public BlockState m_183550_(int x, int y, int z) {
            BlockState a = this.late.m_183550_(x, y, z);
            BlockState b = this.early.m_183550_(x, y, z);
            SHADOW_CHECKS.incrementAndGet();
            if (this.foreign) SHADOW_CHECKS_FOREIGN.incrementAndGet();
            if (a != b && SHADOW_MISMATCHES.incrementAndGet() <= 20)
                LOGGER.warn("Bons and Furious: terrablender_lazy_namespace_rules shadow mismatch at {},{},{}: {} vs {}", x, y, z, a, b);
            return a;
        }
    }
}
