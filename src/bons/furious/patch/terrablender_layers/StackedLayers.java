package bons.furious.patch.terrablender_layers;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.MapMaker;
import com.mojang.logging.LogUtils;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.SurfaceRules;
import org.slf4j.Logger;
import terrablender.worldgen.surface.NamespacedSurfaceRuleSource;

/**
 * Bons and Furious switches terrablender_stacked_layer_dedupe (exact) and terrablender_stacked_layer_save (Fix), TerraBlender
 * 3.0.1.10 and 3.0.1.11 (LGPL-3.0; NamespacedSurfaceRuleSource and SurfaceRuleManager are identical in both), server side of
 * world generation. SRG names for Minecraft members.
 *
 * What it costs. TerraBlender's MixinNoiseGeneratorSettings wraps a NoiseGeneratorSettings' raw surface rule in
 * SurfaceRuleManager.getNamespacedRules(category, raw) = NamespacedSurfaceRuleSource(raw, {minecraft + every registered
 * namespace}) on first access, and that accessor is also the codec getter. When the settings holder is Direct when level.dat
 * is written (old-template MCreator biome injectors such as Terramity's or Under the Moon's, Lithostitched add_surface_rule
 * modifiers, inline presets) the wrapper is saved inline, and when that saved stem is loaded back (no data-pack dimension
 * file replaces it) the next world open wraps the decoded wrapper again: one more full copy of every namespace per open.
 * Each layer re-walks every namespace whose rule answers null, TerraBlender builds every layer's namespace trees per chunk,
 * and level.dat grows by the whole namespace map per open.
 *
 * What the switches do. flatten(top) walks the chain of NamespacedSurfaceRuleSource layers below a top-level one. In every
 * inner layer it drops a namespace entry that equals an entry of the same key in any outer layer (record equality, or the
 * same RuleSource codec encoding), provided both trees are ones LazyNamespaceRules.deferrable accepts (vanilla rule sources
 * with only verified mixins, and the verified Oh The Biomes We've Gone types; TerraBlender's NamespacedRule a tested build);
 * a layer left without entries is
 * removed; the innermost base is kept; surviving entries keep their order. The dedupe switch applies NamespacedSurfaceRule-
 * Source.apply's base through that flattened chain (memoised per top-level source; the saved structure is untouched). The
 * save switch returns the flattened chain from SurfaceRuleManager.getNamespacedRules, so TerraBlender caches it, generates
 * from it and saves it: a world that stopped changing keeps one layer, an already stacked one collapses at its next save.
 *
 * Why the blocks are identical. NamespacedRule.tryApply asks the rule of the biome's namespace and, when it answers null, the
 * base rule; in a chain that is the next layer's NamespacedRule. So an inner entry for namespace n is asked only after every
 * outer layer's entry for n (where present) answered null at that same call. A rule built from a source equal to one of those
 * answers the same for the same context state: the verified sources' rules read only per-context constants and the context
 * state of the call (their lazy conditions cache per context counter, the shared context conditions are the same objects,
 * positional randoms are fresh per position); so the dropped entry would have answered null as well, and skipping it changes
 * no block and no call order of the remaining rules. Surface rules draw no shared random state. A pass-through layer (no
 * entries) only forwards to its base.
 *
 * -Dbons_and_furious.stackedLayerDedupe=false / -Dbons_and_furious.stackedLayerSave=false turn them off (checked per call).
 * -Dbons_and_furious.stackedLayerDedupe.shadow=true (verification runs only): every flattened apply also builds the full
 * chain's base rule and compares both at every tryApply (SHADOW_CHECKS / SHADOW_MISMATCHES); the flattened answer is used.
 * Extends SurfaceRules only to name its protected nested types; never instantiated.
 */
public final class StackedLayers extends SurfaceRules {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static volatile boolean dedupeEnabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.stackedLayerDedupe", "true"));
    public static volatile boolean saveEnabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.stackedLayerSave", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.stackedLayerDedupe.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    /** Applies through a flattened chain / saved chains flattened / entries and layers dropped (diagnostics; the proof reads them). */
    public static final AtomicLong FLATTENED_APPLY = new AtomicLong(), FLATTENED_SAVE = new AtomicLong(), DROPPED_ENTRIES = new AtomicLong(),
            DROPPED_LAYERS = new AtomicLong();
    static final int MAX_LAYERS = 4096;
    /** Top-level source -> the base its apply uses (the flattened chain, or its own base when nothing is dropped). Weak keys. */
    private static final ConcurrentMap<Object, SurfaceRules.RuleSource> BASES = new MapMaker().weakKeys().makeMap();
    /**
     * The purity proof of the terrablender group (bons.furious.patch.terrablender.LazyNamespaceRules.deferrable), resolved by
     * name so this group compiles on its own; it is in the same jar. Missing (never in a release): no entry is ever dropped.
     */
    private static final MethodHandle DEFERRABLE = deferrableHandle();

    private static MethodHandle deferrableHandle() {
        try {
            Class<?> c = Class.forName("bons.furious.patch.terrablender.LazyNamespaceRules", false, StackedLayers.class.getClassLoader());
            return MethodHandles.publicLookup().findStatic(c, "deferrable", MethodType.methodType(boolean.class, SurfaceRules.RuleSource.class));
        } catch (ReflectiveOperationException | LinkageError e) {
            LOGGER.warn("Bons and Furious: terrablender_stacked_layer_dedupe / _save cannot reach the surface-rule purity check ({}); stacked layers are left as they are", e.toString());
            return null;
        }
    }

    /** True when LazyNamespaceRules proves the tree pure (vanilla and verified sources, verified mixins, tested TerraBlender). */
    static boolean pure(SurfaceRules.RuleSource source) {
        if (DEFERRABLE == null) return false;
        try {
            return (boolean) DEFERRABLE.invokeExact(source);
        } catch (Throwable t) {
            return false;
        }
    }
    private static volatile boolean announcedApply, announcedSave;

    private StackedLayers() {
    }

    /** terrablender_stacked_layer_dedupe: the base NamespacedSurfaceRuleSource.apply applies for this top-level source. */
    public static SurfaceRules.RuleSource baseFor(Object top, SurfaceRules.RuleSource base) {
        if (!dedupeEnabled || !(base instanceof NamespacedSurfaceRuleSource) || !(top instanceof NamespacedSurfaceRuleSource t)) return base;
        SurfaceRules.RuleSource known = BASES.get(t);
        if (known == null) {
            Flat f = flatten(t);
            SurfaceRules.RuleSource prev = BASES.putIfAbsent(t, f.base);
            known = prev != null ? prev : f.base;
            if (prev == null && f.base != base && !announcedApply) {
                announcedApply = true;
                LOGGER.info("Bons and Furious: terrablender_stacked_layer_dedupe applies: a TerraBlender surface rule had {} stacked layers; {} namespace entries equal to an outer layer's are skipped, {} layers evaluated{}",
                        f.layers + 1, f.dropped, f.left + 1, SHADOW ? " - shadow verification on" : "");
            }
        }
        if (known != base) FLATTENED_APPLY.incrementAndGet();
        return known;
    }

    /** terrablender_stacked_layer_save: the source TerraBlender caches as the surface rule (and so saves). */
    public static SurfaceRules.RuleSource forSave(SurfaceRules.RuleSource built) {
        if (!saveEnabled || !(built instanceof NamespacedSurfaceRuleSource top) || !(top.base() instanceof NamespacedSurfaceRuleSource)) return built;
        Flat f = flatten(top);
        if (f.base == top.base()) return built;
        NamespacedSurfaceRuleSource out = new NamespacedSurfaceRuleSource(f.base, top.sources());
        BASES.put(out, f.base);                 // already flat: the dedupe switch has nothing more to drop
        FLATTENED_SAVE.incrementAndGet();
        if (!announcedSave) {
            announcedSave = true;
            LOGGER.info("Bons and Furious: terrablender_stacked_layer_save: a TerraBlender surface rule decoded with {} stacked layers is kept with {} ({} namespace entries equal to an outer layer's dropped); it is what generates and what is saved",
                    f.layers + 1, f.left + 1, f.dropped);
        }
        return out;
    }

    /** The flattened chain below top and what was dropped. base == top.base() when nothing is. */
    public record Flat(SurfaceRules.RuleSource base, int layers, int left, int dropped) {}

    /** Public for the proof. */
    public static Flat flatten(NamespacedSurfaceRuleSource top) {
        List<NamespacedSurfaceRuleSource> layers = new ArrayList<>();
        SurfaceRules.RuleSource r = top.base();
        while (r instanceof NamespacedSurfaceRuleSource n && layers.size() < MAX_LAYERS) {
            layers.add(n);
            r = n.base();
        }
        if (r instanceof NamespacedSurfaceRuleSource) return new Flat(top.base(), layers.size(), layers.size(), 0);   // absurdly deep: left alone
        SurfaceRules.RuleSource innermost = r;
        Map<String, List<SurfaceRules.RuleSource>> outer = new HashMap<>();
        addAll(outer, top.sources());
        Map<Object, Optional<Tag>> encodings = new IdentityHashMap<>();
        List<Map<String, SurfaceRules.RuleSource>> kept = new ArrayList<>(layers.size());
        int dropped = 0;
        boolean changed = false;
        for (NamespacedSurfaceRuleSource layer : layers) {
            Map<String, SurfaceRules.RuleSource> keep = new LinkedHashMap<>();
            for (Map.Entry<String, SurfaceRules.RuleSource> e : layer.sources().entrySet()) {
                SurfaceRules.RuleSource v = e.getValue();
                if (v != null && hasEqual(outer.get(e.getKey()), v, encodings)) dropped++;
                else keep.put(e.getKey(), v);
            }
            if (keep.size() != layer.sources().size() || keep.isEmpty()) changed = true;
            addAll(outer, layer.sources());     // the layer's own entries are outer to every deeper layer
            kept.add(keep);
        }
        if (!changed) return new Flat(top.base(), layers.size(), layers.size(), 0);
        SurfaceRules.RuleSource base = innermost;
        int left = 0;
        for (int i = layers.size() - 1; i >= 0; i--) {
            Map<String, SurfaceRules.RuleSource> keep = kept.get(i);
            if (keep.isEmpty()) continue;       // a pass-through layer
            NamespacedSurfaceRuleSource layer = layers.get(i);
            base = keep.size() == layer.sources().size() && base == layer.base() ? layer : new NamespacedSurfaceRuleSource(base, ImmutableMap.copyOf(keep));
            left++;
        }
        DROPPED_ENTRIES.addAndGet(dropped);
        DROPPED_LAYERS.addAndGet(layers.size() - left);
        return new Flat(base, layers.size(), left, dropped);
    }

    private static void addAll(Map<String, List<SurfaceRules.RuleSource>> outer, Map<String, SurfaceRules.RuleSource> sources) {
        for (Map.Entry<String, SurfaceRules.RuleSource> e : sources.entrySet()) outer.computeIfAbsent(e.getKey(), k -> new ArrayList<>(2)).add(e.getValue());
    }

    /**
     * True when an outer entry of the same key is equal to v and both trees are proven pure (LazyNamespaceRules.deferrable).
     * Equal = the same object, record equality, or the same encoding by RuleSource's own codec: every verified source type
     * is fully described by its codec fields (the copies level.dat stores ARE that encoding decoded again), so two sources
     * encoding alike build rules that answer alike; Oh The Biomes We've Gone's types compare their weighted lists by identity,
     * which only the encoding sees through. A source the codec cannot encode is never equal by encoding.
     */
    private static boolean hasEqual(List<SurfaceRules.RuleSource> seen, SurfaceRules.RuleSource v, Map<Object, Optional<Tag>> encodings) {
        if (seen == null || !pure(v)) return false;
        Optional<Tag> ev = null;
        for (SurfaceRules.RuleSource s : seen) {
            if (s == v) return true;
            if (s == null || !pure(s)) continue;
            if (s.equals(v)) return true;
            if (ev == null) ev = encode(v, encodings);
            if (ev.isPresent() && ev.equals(encode(s, encodings))) return true;
        }
        return false;
    }

    private static Optional<Tag> encode(SurfaceRules.RuleSource source, Map<Object, Optional<Tag>> encodings) {
        return encodings.computeIfAbsent(source, s -> {
            try {
                return SurfaceRules.RuleSource.f_189682_.encodeStart(NbtOps.f_128958_, (SurfaceRules.RuleSource) s).result();
            } catch (Throwable t) {
                return Optional.empty();
            }
        });
    }

    /** Shadow mode: the flattened chain's base rule answers; the full chain's is asked too and compared. */
    public static Object shadow(Object late, Object early) {
        return new Shadowed((SurfaceRules.SurfaceRule) late, (SurfaceRules.SurfaceRule) early);
    }

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
                LOGGER.warn("Bons and Furious: terrablender_stacked_layer_dedupe shadow mismatch at {},{},{}: {} vs {}", x, y, z, a, b);
            return a;
        }
    }
}
