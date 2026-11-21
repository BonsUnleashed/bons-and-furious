package bons.furious.patch.formations_biome_first;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.logging.LogUtils;
import com.supermartijn642.formations.structure.FormationsSinglePoolElement;
import com.supermartijn642.formations.structure.StructurePlacement;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import org.slf4j.Logger;

/**
 * Bons and Furious switch formations_structure_biome_first (Formations 1.0.4 for NeoForge 1.21 with Formations Nether /
 * Overworld; server side of world generation; OFF by default: see the trade-off below). Ported from the Forge 1.20.1 switch:
 * Mojang member names, the logic is unchanged.
 *
 * <p>Formations' structures (SimpleStructure, PiecedStructure) draw their start x and z, then run their placement search
 * (StructurePlacement.findHeight: five positions, each a ground-height query and a whole-column read) and only then does
 * Minecraft test the structure's biome at the stub (Structure.findValidGenerationPoint: findGenerationPoint(context)
 * .filter(isValidBiome), the biome of chunkGenerator.getBiomeSource().getNoiseBiome(stub quart, sampler) tested with the
 * context's validBiome). A structure set tries its structures one after another in the same candidate chunk until one is
 * placed (Formations Nether: 18 in one set; Formations Overworld: 27), and most of them allow only a few biomes, so most
 * placement searches end in a biome the structure does not allow. In the Forge 1.20.1 pack it was written for, that search
 * was 13.1 % of Nether world generation (profiles of 2026-11-20); on 1.21.1 it is not measured.
 *
 * <p>The switch asks the biome first. The stub's x and z are known before the search (SimpleStructure: its x and z;
 * PiecedStructure: the centre of the start piece's box, whose x and z do not move with its height for a single pool
 * element) and the search can only return a height h in [min build height, max build height] (every locator returns a
 * heightmap height, an average of them, or a block of a whole-column read, guarded by fingerprint), so the stub's y is one of
 * [max(min, min - groundLevelDelta + 1), max - groundLevelDelta + 1] (SimpleStructure keeps y >= min) or [min + 1, max + 1]
 * (PiecedStructure). The switch looks up the biome of every quart of that range in the stub's column, top down (the top
 * quart answers for every surface biome), with the same call as isValidBiome, and stops at the first biome the structure
 * allows: then the search runs as shipped. When no quart of the range has an allowed biome, no height the search could
 * return passes the biome test, so the attempt ends at once with the empty result the biome test would have produced (the
 * other mods' hooks on these methods in this pack, reviewed 2026-11-20, only reject more: Structurify's checks after
 * generate, companions' and Bons' guards, structure_gel's and BetterEnd's switches). The attempt's random is its own
 * (one WorldgenRandom per attempt), so skipping the search changes no other draw. The lookups of one column are kept per
 * thread for the next attempts (two columns; a candidate chunk's attempts share the same few columns).
 *
 * <p>Trade-off: the climate tree answers an exact tie between two biomes with the one its thread found last
 * (Climate.RTree.lastResult). The extra lookups (and the kept column) change that history on the worker thread, so where two
 * biomes tie exactly an attempt or a later lookup can take the other of the two. Vanilla's own answer at such a tie already
 * depends on what the worker thread generated before. Any other start element type, a chunk generator that is not a
 * NoiseBasedChunkGenerator or overrides one of its height methods or getBiomeSource, or any failure keeps the shipped order.
 *
 * <p>-Dbons_and_furious.formationsBiomeFirst=false keeps the shipped order (the config switch acts at class transformation);
 * .metrics=true counts attempts, skips, lookups and kept answers and prints them at JVM exit; .verify=true (rigs) never skips:
 * every search runs, and at the return of findGenerationPoint the stub is checked against the prediction (x, z, y range) and,
 * for an attempt the switch would have skipped, its biome is tested: a run without "wrong" or "mispredicted" lines shows the
 * skip never drops a structure the biome test would have kept.
 *
 * <p>Ported to 1.21.1: Formations 1.0.4 for NeoForge 1.21 has the same SimpleStructure / PiecedStructure.findGenerationPoint,
 * StructurePlacement locators and lambdas as the Forge build (the same calls and field accesses; addPieces passes Minecraft
 * 1.21's liquid settings to the start piece, which does not move its box); Minecraft's
 * Structure.findValidGenerationPoint / isValidBiome and the generator's height methods are unchanged where this reads them.
 * The other mods of the Forge pack whose hooks on these methods were reviewed are not part of the 1.21.1 test fixtures.
 */
public final class BiomeFirst {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final boolean VERIFY = Boolean.getBoolean("bons_and_furious.formationsBiomeFirst.verify");
    public static final boolean ACTIVE = !VERIFY && !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.formationsBiomeFirst", "true"));
    public static final boolean METRICS = VERIFY || Boolean.getBoolean("bons_and_furious.formationsBiomeFirst.metrics");
    /** False when Formations runs exactly as shipped and nothing is counted. */
    public static final boolean ON = ACTIVE || METRICS;
    /** Decisions passed from the findHeight wrap to the verify check at findGenerationPoint's return. */
    public static final int NONE = 0, PASS = 1, SKIP = 2;
    public static final LongAdder ATTEMPTS = new LongAdder(), SKIPPED = new LongAdder(), LOOKUPS = new LongAdder(), KEPT_ANSWERS = new LongAdder(),
            OTHER = new LongAdder(), OTHER_GENERATOR = new LongAdder(), CHECKED = new LongAdder(), WRONG = new LongAdder(),
            MISPREDICTED = new LongAdder();
    private static volatile boolean announced;

    static {
        if (METRICS) {
            Runtime.getRuntime().addShutdownHook(new Thread(() -> System.out.println("Bons and Furious formations_structure_biome_first: attempts "
                    + ATTEMPTS.sum() + ", " + (VERIFY ? "the switch would skip " : "skipped ") + SKIPPED.sum() + ", biome lookups " + LOOKUPS.sum()
                    + ", kept answers used " + KEPT_ANSWERS.sum() + ", left as shipped: other start element type " + OTHER.sum()
                    + ", generator with its own height methods " + OTHER_GENERATOR.sum()
                    + (VERIFY ? ", stubs checked " + CHECKED.sum() + ", wrong " + WRONG.sum() + ", mispredicted " + MISPREDICTED.sum() : "")),
                    "Bons and Furious Formations biome-first summary"));
        }
    }

    private BiomeFirst() {
    }

    /** The column a stub can stand in and the quart range of its possible heights (as the findHeight wrap predicted it). */
    public static final class Prediction {
        public int decision = NONE, x, z, yLo, yHi;
    }

    /** True for the start elements whose ground level is a plain getter and whose box only moves with its position. */
    public static boolean knownElement(StructurePoolElement element) {
        Class<?> c = element == null ? null : element.getClass();
        return c == FormationsSinglePoolElement.class || c == SinglePoolElement.class;
    }

    /**
     * SimpleStructure: the stub is (x, h - groundLevelDelta + 1, z) for a search height h, kept when its y is at least the
     * minimum build height.
     */
    public static Optional<Integer> simple(StructurePlacement placement, Structure.GenerationContext context, BoundingBox box,
            Operation<Optional<Integer>> original, int x, int z, StructurePoolElement element, Prediction p) {
        if (!eligible(context, element)) return original.call(placement, context, box);
        LevelHeightAccessor h = context.heightAccessor();
        int min = h.getMinBuildHeight(), max = h.getMaxBuildHeight(), ground = element.getGroundLevelDelta();
        long lo = Math.max((long) min, (long) min - ground + 1), hi = (long) max - ground + 1;
        return decide(placement, context, box, original, x, z, lo, hi, p);
    }

    /** PiecedStructure: the stub is the centre of the start piece's box, at the search height + 1. */
    public static Optional<Integer> pieced(StructurePlacement placement, Structure.GenerationContext context, BoundingBox box,
            Operation<Optional<Integer>> original, StructurePoolElement element, Prediction p) {
        if (!eligible(context, element)) return original.call(placement, context, box);
        LevelHeightAccessor h = context.heightAccessor();
        int cx = (box.maxX() + box.minX()) / 2, cz = (box.maxZ() + box.minZ()) / 2;
        return decide(placement, context, box, original, cx, cz, (long) h.getMinBuildHeight() + 1, (long) h.getMaxBuildHeight() + 1, p);
    }

    private static boolean eligible(Structure.GenerationContext context, StructurePoolElement element) {
        if (!ON) return false;
        ATTEMPTS.increment();
        if (!knownElement(element)) {
            OTHER.increment();
            return false;
        }
        if (context.chunkGenerator() == null || !PLAIN_HEIGHTS.get(context.chunkGenerator().getClass())) {
            OTHER_GENERATOR.increment();
            return false;
        }
        return true;
    }

    /** ChunkGenerator getBaseHeight, getBaseColumn, getFirstFreeHeight, getFirstOccupiedHeight, getBiomeSource. */
    private static final Set<String> HEIGHT_METHODS = Set.of("getBaseHeight", "getBaseColumn", "getFirstFreeHeight", "getFirstOccupiedHeight", "getBiomeSource");

    /**
     * True for NoiseBasedChunkGenerator and for its subclasses that declare none of HEIGHT_METHODS (BCLib's Nether generator, for
     * example): their heights are blocks of NoiseBasedChunkGenerator's own column walk, so the height range above holds, and their
     * biome source is the one the biome test asks. Decided once per class.
     */
    private static final ClassValue<Boolean> PLAIN_HEIGHTS = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> c) {
            try {
                if (!NoiseBasedChunkGenerator.class.isAssignableFrom(c)) return false;
                for (Class<?> k = c; k != NoiseBasedChunkGenerator.class; k = k.getSuperclass()) {
                    for (Method m : k.getDeclaredMethods()) if (HEIGHT_METHODS.contains(m.getName())) return false;
                }
                return true;
            } catch (Throwable t) {
                return false;
            }
        }
    };

    private static Optional<Integer> decide(StructurePlacement placement, Structure.GenerationContext context, BoundingBox box,
            Operation<Optional<Integer>> original, int x, int z, long lo, long hi, Prediction p) {
        if (lo < -(1 << 20) || hi > (1 << 20)) {         // a ground level no world height comes near: the shipped order
            OTHER.increment();
            return original.call(placement, context, box);
        }
        boolean skip;
        try {
            skip = noAllowedBiome(context, x, z, lo, hi);
        } catch (Throwable t) {                  // anything unexpected: the shipped order
            return original.call(placement, context, box);
        }
        if (p != null) {
            p.decision = skip ? SKIP : PASS;
            p.x = x;
            p.z = z;
            p.yLo = (int) lo;
            p.yHi = (int) hi;
        }
        if (skip) SKIPPED.increment();
        if (skip && ACTIVE) {
            if (!announced) {
                announced = true;
                LOGGER.info("Bons and Furious: formations_structure_biome_first applies (Formations structures test their biome before "
                        + "their placement search)");
            }
            return Optional.empty();
        }
        return original.call(placement, context, box);
    }

    /** True when no quart of the stub's possible heights has a biome the structure allows (lo > hi: no height at all). */
    static boolean noAllowedBiome(Structure.GenerationContext context, int x, int z, long lo, long hi) {
        if (lo > hi) return true;
        BiomeSource source = context.chunkGenerator().getBiomeSource();
        Climate.Sampler sampler = context.randomState().sampler();
        Predicate<Holder<Biome>> allowed = context.validBiome();
        LevelHeightAccessor h = context.heightAccessor();
        int qx = QuartPos.fromBlock(x), qz = QuartPos.fromBlock(z);
        int qLo = QuartPos.fromBlock((int) lo), qHi = QuartPos.fromBlock((int) hi);
        Column column = Columns.get(source, sampler, qx, qz, h.getMinBuildHeight(), h.getMaxBuildHeight());
        for (int qy = qHi; qy >= qLo; qy--) {
            if (allowed.test(column.biome(source, sampler, qx, qy, qz))) return false;
        }
        return true;
    }

    /** One column's looked-up biomes, indexed by quart y - base (null = not looked up yet). */
    static final class Column {
        BiomeSource source;
        Climate.Sampler sampler;
        int qx, qz, min, max, base;
        Holder<Biome>[] biomes;

        @SuppressWarnings("unchecked")
        void reset(BiomeSource source, Climate.Sampler sampler, int qx, int qz, int min, int max) {
            this.source = source;
            this.sampler = sampler;
            this.qx = qx;
            this.qz = qz;
            this.min = min;
            this.max = max;
            base = QuartPos.fromBlock(min) - 1;
            int n = QuartPos.fromBlock(max + 1) - base + 2;
            if (biomes == null || biomes.length < n) biomes = (Holder<Biome>[]) new Holder<?>[n];
            else Arrays.fill(biomes, null);
        }

        boolean is(BiomeSource source, Climate.Sampler sampler, int qx, int qz, int min, int max) {
            return this.source == source && this.sampler == sampler && this.qx == qx && this.qz == qz && this.min == min && this.max == max;
        }

        Holder<Biome> biome(BiomeSource source, Climate.Sampler sampler, int qx, int qy, int qz) {
            int i = qy - base;
            if (i >= 0 && i < biomes.length && biomes[i] != null) {
                KEPT_ANSWERS.increment();
                return biomes[i];
            }
            LOOKUPS.increment();
            Holder<Biome> b = source.getNoiseBiome(qx, qy, qz, sampler);
            if (i >= 0 && i < biomes.length) biomes[i] = b;
            return b;
        }
    }

    /** The last two columns of this thread. */
    static final class Columns {
        private static final ThreadLocal<Columns> MINE = ThreadLocal.withInitial(Columns::new);
        final Column a = new Column(), b = new Column();
        boolean aLast;

        static Column get(BiomeSource source, Climate.Sampler sampler, int qx, int qz, int min, int max) {
            Columns c = MINE.get();
            if (c.a.biomes != null && c.a.is(source, sampler, qx, qz, min, max)) {
                c.aLast = true;
                return c.a;
            }
            if (c.b.biomes != null && c.b.is(source, sampler, qx, qz, min, max)) {
                c.aLast = false;
                return c.b;
            }
            Column old = c.aLast ? c.b : c.a;
            old.reset(source, sampler, qx, qz, min, max);
            c.aLast = old == c.a;
            return old;
        }
    }

    /**
     * Verify mode, at findGenerationPoint's return: the stub against the prediction, and for an attempt the switch would have
     * skipped, the biome test isValidBiome makes (a fresh lookup).
     */
    public static void verify(Structure.GenerationContext context, Optional<Structure.GenerationStub> result, Prediction p, String structure) {
        if (!VERIFY || p == null || p.decision == NONE) return;
        CHECKED.increment();
        if (result == null || result.isEmpty()) return;
        BlockPos pos = result.get().position();
        if (pos.getX() != p.x || pos.getZ() != p.z || pos.getY() < p.yLo || pos.getY() > p.yHi) {
            if (MISPREDICTED.sum() < 20) {
                LOGGER.error("Bons and Furious: formations_structure_biome_first mispredicted a {} stub: {} {} {} outside column {} {} heights {}..{}",
                        structure, pos.getX(), pos.getY(), pos.getZ(), p.x, p.z, p.yLo, p.yHi);
            }
            MISPREDICTED.increment();
        }
        if (p.decision != SKIP) return;
        boolean valid = context.validBiome().test(context.chunkGenerator().getBiomeSource().getNoiseBiome(QuartPos.fromBlock(pos.getX()),
                QuartPos.fromBlock(pos.getY()), QuartPos.fromBlock(pos.getZ()), context.randomState().sampler()));
        if (valid) {
            if (WRONG.sum() < 20) {
                LOGGER.error("Bons and Furious: formations_structure_biome_first would have skipped a {} at {} {} {} whose biome the structure allows",
                        structure, pos.getX(), pos.getY(), pos.getZ());
            }
            WRONG.increment();
        }
    }
}
