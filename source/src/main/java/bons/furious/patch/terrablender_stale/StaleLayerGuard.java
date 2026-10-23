package bons.furious.patch.terrablender_stale;

import com.google.common.collect.MapMaker;
import com.mojang.logging.LogUtils;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.SurfaceRules;
import org.slf4j.Logger;

/**
 * Bons and Furious switch terrablender_stale_layer_guard (Fix; TerraBlender 3.0.1.10 and 3.0.1.11, LGPL-3.0; Minecraft
 * 1.20.1, server side of world generation). SRG names for Minecraft members. Extends SurfaceRules only to name its
 * protected nested types; never instantiated.
 *
 * The bug. TerraBlender's MixinNoiseGeneratorSettings wraps a NoiseGeneratorSettings' raw surface rule in a
 * NamespacedSurfaceRuleSource (the "minecraft" default rules plus every mod's namespace) on first access, and that accessor
 * is also the codec getter. When the overworld settings holder is Direct (old-template MCreator biome injectors, inline
 * presets, Lithostitched surface rule modifiers) level.dat saves the wrapper, and the next open wraps the decoded one again,
 * so a world keeps one saved layer per open (VanillaBackport 1.1.7+ also prepends its own rules at every settings
 * construction, so the layers sit inside sequences). NamespacedSurfaceRuleSource.apply builds every namespace entry of every
 * layer for every chunk. A saved layer keeps the rules as they were when it was saved: when a mod that registered a noise
 * those rules use is removed or changed to a version without it (VanillaBackport 1.2 adds minecraft:small_patch, 1.1.7.10
 * does not), RandomState.getOrCreateNoise throws "Missing element ResourceKey[minecraft:worldgen/noise / ...]" and every
 * new chunk of that dimension crashes. Plain TerraBlender does this; nothing else in the world needs the old rule.
 *
 * What the switch does. NamespacedSourceStaleGuardMixin counts, per thread, the base.apply calls of NamespacedSurfaceRule-
 * Source that are running: a NamespacedSurfaceRuleSource applied while one is above zero is an inner layer, i.e. one that
 * was read back from saved data (TerraBlender's own wrapper is always the outermost one). For an inner layer only, each
 * namespace entry TerraBlender builds is handed over as a GuardedSource around the original value (after
 * terrablender_lazy_namespace_rules, if that switch defers it). Its build, at once or at its first use, runs the original
 * call; when that call throws the missing-noise exception, this layer's rules for that namespace answer nothing from then
 * on, exactly as if the saved layer had no entry for it (TerraBlender's NamespacedRule then asks the next layer and the
 * base, in both tested builds), and one WARN names the namespace and the noise. Any other exception is rethrown unchanged.
 * The outermost (current) layer is never wrapped, so a mod whose current rules ask for a noise it does not ship still
 * crashes as before.
 *
 * Why nothing else changes. Where no build fails, a GuardedSource's rule returns exactly what the original rule returns, at
 * the same calls (one delegating call and a flag test); TerraBlender's map has the same keys in the same order; nothing in
 * TerraBlender or Minecraft compares the rule objects. A layer's failure is remembered per (layer object, namespace) in a
 * weak identity map: the layer objects are the decoded ones of one loaded world, whose noise registry does not change
 * while it is loaded, so the same build would fail again.
 *
 * -Dbons_and_furious.staleLayerGuard=false turns it off (checked per surface pass).
 */
public final class StaleLayerGuard extends SurfaceRules {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.staleLayerGuard", "true"));
    /** Inner-layer entries handed over guarded / builds that failed for a missing noise / applies answered as skipped. */
    public static final AtomicLong GUARDED = new AtomicLong(), FAILED = new AtomicLong(), SKIPPED = new AtomicLong();
    /** HolderGetter.getOrThrow's message for a noise key the registry does not have (ResourceKey.toString inside). */
    static final String MISSING_NOISE = "Missing element ResourceKey[minecraft:worldgen/noise / ";
    /** Per thread: how many NamespacedSurfaceRuleSource base.apply calls are running. */
    private static final ThreadLocal<int[]> DEPTH = ThreadLocal.withInitial(() -> new int[1]);
    /** Inner layer (weak key, identity) -> namespace -> its state. */
    private static final ConcurrentMap<Object, ConcurrentMap<String, Cell>> CELLS = new MapMaker().weakKeys().makeMap();
    /** "namespace|noise" pairs already reported with a WARN (later layers with the same pair log at DEBUG). */
    private static final Set<String> REPORTED = ConcurrentHashMap.newKeySet();

    private StaleLayerGuard() {
    }

    /** Around NamespacedSurfaceRuleSource.apply's base.apply call. */
    public static void enterBase() {
        DEPTH.get()[0]++;
    }

    public static void exitBase() {
        DEPTH.get()[0]--;
    }

    /** The consumer NamespacedSurfaceRuleSource.apply hands to forEach: the original one, or (inner layer) a guarding one. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static Consumer consumer(Object layer, Consumer original) {
        if (!enabled || DEPTH.get()[0] == 0) return original;
        return entry -> original.accept(guard(layer, (Map.Entry<String, ?>) entry));
    }

    /** Public for the proof. The entry an inner layer's forEach passes on: the same key with a GuardedSource. */
    public static Map.Entry<String, ?> guard(Object layer, Map.Entry<String, ?> entry) {
        if (!(entry.getValue() instanceof SurfaceRules.RuleSource source) || entry.getKey() == null) return entry;
        Cell cell = CELLS.computeIfAbsent(layer, k -> new ConcurrentHashMap<>()).computeIfAbsent(entry.getKey(), Cell::new);
        GuardedSource g = cell.last;
        if (g == null || g.source != source) {               // the same saved source each chunk (built up front): reused
            g = new GuardedSource(source, cell);
            cell.last = g;
        }
        GUARDED.incrementAndGet();
        return Map.entry(entry.getKey(), g);
    }

    /** True (and remembered, reported once) when t is the missing-noise exception; false for anything else. */
    static boolean failed(Cell cell, Throwable t) {
        String noise = missingNoise(t);
        if (noise == null) return false;
        FAILED.incrementAndGet();
        if (cell.missing == null) {
            cell.missing = noise;
            if (REPORTED.add(cell.namespace + '|' + noise))
                LOGGER.warn("Bons and Furious: terrablender_stale_layer_guard: an older copy of TerraBlender's surface rules that this world keeps in its "
                        + "level.dat has \"{}\" rules that need the noise {}, which no installed mod or data pack provides (a mod was removed, or changed "
                        + "to a version without it, after this world was opened with it). TerraBlender rebuilds every saved copy for every new chunk, so "
                        + "chunk generation would crash here; that saved copy's \"{}\" rules are skipped instead and the current rules still apply.",
                        cell.namespace, noise, cell.namespace);
            else
                LOGGER.debug("Bons and Furious: terrablender_stale_layer_guard: another saved copy's \"{}\" rules need {} too; skipped", cell.namespace, noise);
        }
        return true;
    }

    /** Public for the proof. The noise id in the missing-noise exception (or one it wraps), else null. */
    public static String missingNoise(Throwable t) {
        for (int i = 0; t != null && i < 8; t = t.getCause(), i++) {
            String m = t.getMessage();
            if (t instanceof IllegalStateException && m != null && m.startsWith(MISSING_NOISE)) {
                int end = m.lastIndexOf(']');
                return end > MISSING_NOISE.length() ? m.substring(MISSING_NOISE.length(), end) : m.substring(MISSING_NOISE.length());
            }
        }
        return null;
    }

    /** Public for the proof: whether a saved layer's namespace was found to need this missing noise (and reported). */
    public static boolean reported(String namespace, String noise) {
        return REPORTED.contains(namespace + '|' + noise);
    }

    static final class Cell {
        final String namespace;
        volatile String missing;
        volatile GuardedSource last;

        Cell(String namespace) {
            this.namespace = namespace;
        }
    }

    /** An inner layer's namespace entry: builds the original value; a missing noise makes it answer nothing. */
    public static final class GuardedSource implements SurfaceRules.RuleSource {
        final SurfaceRules.RuleSource source;
        final Cell cell;

        GuardedSource(SurfaceRules.RuleSource source, Cell cell) {
            this.source = source;
            this.cell = cell;
        }

        @Override
        public KeyDispatchDataCodec<? extends SurfaceRules.RuleSource> codec() {
            return this.source.codec();
        }

        @Override
        public SurfaceRules.SurfaceRule apply(SurfaceRules.Context context) {
            if (this.cell.missing != null) {
                SKIPPED.incrementAndGet();
                return NONE;
            }
            SurfaceRules.SurfaceRule rule;
            try {
                rule = this.source.apply(context);
            } catch (IllegalStateException e) {
                if (!failed(this.cell, e)) throw e;
                SKIPPED.incrementAndGet();
                return NONE;
            }
            return new GuardedRule(rule, this.cell);
        }
    }

    /** The guarded rule: the original rule's answers; after a missing-noise failure (a build deferred to first use), none. */
    static final class GuardedRule implements SurfaceRules.SurfaceRule {
        private final SurfaceRules.SurfaceRule rule;
        private final Cell cell;
        private boolean dead;

        GuardedRule(SurfaceRules.SurfaceRule rule, Cell cell) {
            this.rule = rule;
            this.cell = cell;
        }

        @Override
        public BlockState tryApply(int x, int y, int z) {
            if (this.dead) return null;
            try {
                return this.rule.tryApply(x, y, z);
            } catch (IllegalStateException e) {
                if (!failed(this.cell, e)) throw e;
                this.dead = true;
                return null;
            }
        }
    }

    /** A rule that answers nothing (TerraBlender's NamespacedRule then asks the next layer and the base). */
    static final SurfaceRules.SurfaceRule NONE = (x, y, z) -> null;
}
