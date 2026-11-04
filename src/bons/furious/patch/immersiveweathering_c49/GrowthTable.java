package bons.furious.patch.immersiveweathering_c49;

import com.google.common.base.Suppliers;
import com.ordana.immersive_weathering.configs.CommonConfigs;
import com.ordana.immersive_weathering.data.block_growths.IConditionalGrowingBlock;
import com.ordana.immersive_weathering.data.block_growths.TickSource;
import com.ordana.immersive_weathering.data.block_growths.growths.ConfigurableBlockGrowth;
import com.ordana.immersive_weathering.data.block_growths.growths.IBlockGrowth;
import com.ordana.immersive_weathering.data.block_growths.growths.builtin.NoOpBlockGrowth;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.ThreadingDetector;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switches immersiveweathering_growth_table and immersiveweathering_growth_roll_batch (Immersive
 * Weathering 1.20.1-2.0.5, LGPLv3; server side incl. the integrated server). Immersive Weathering's tickBlock is carried
 * here in a modified form (LGPLv3 permits it; the license note is in the group's patches file).
 *
 * What Immersive Weathering does. Its ServerLevel mixin calls BlockGrowthHandler.tickBlock for EVERY random-tick sample of
 * every randomly ticking section, whatever block was sampled (air, stone, dirt...), and for one sky sample per chunk per
 * tick. Each call reads the block_growths config, checks IConditionalGrowingBlock, allocates a memoizing biome supplier
 * (Suppliers.memoize + a capturing lambda), looks up the universal growth set (EnumMap) and the block's set (EnumMap ->
 * Optional -> lambda -> IdentityHashMap -> Optional) and runs tryGrowing for every growth of both sets. Stone and grass
 * blocks carry the two frost growths of the bundled data, whose growth chance is 0, so they run without any effect; a
 * grass block carries 17 more growths, each drawing level.random.nextFloat() (a compare-and-set on LegacyRandomSource's
 * AtomicLong) to almost always fail its chance of 0.0005-0.003.
 *
 * immersiveweathering_growth_table. Per tick source a table built from the live maps: the universal growths and, per
 * block, the block's growths, each in its HashSet's iteration order, without exactly the entries that cannot do anything:
 * a NoOpBlockGrowth (its tryGrowing is empty) and a plain ConfigurableBlockGrowth (the class itself, not a subclass) with a
 * growth chance of 0 (its private canGrow returns before any draw, and tryGrowing does nothing else). A table is used only
 * while the map objects it was built from are still the ones Immersive Weathering holds (each reload's parse clears both
 * maps and fills them with new sets and maps; nothing else in Immersive Weathering writes them), and is rebuilt otherwise.
 * tickBlock keeps its own order: the config read, the IConditionalGrowingBlock check, then, only when a growth remains,
 * the same Guava memoizing biome supplier and the same tryGrowing calls in the same order with the same arguments.
 *
 * immersiveweathering_growth_roll_batch (exact variant, needs the table). Inside that loop, a run of plain
 * ConfigurableBlockGrowth entries rolls its chances on a local copy of level.random's seed with LegacyRandomSource's own
 * step (seed * 0x5DEECE66D + 11 mod 2^48; nextFloat = (seed >> 24) * 2^-24): the same values, drawn in the same order, as
 * each growth's canGrow would draw. A failing roll is all canGrow would have done (one draw, return false). Before a
 * passing roll the seed is written back as it was before that draw, so the growth's own tryGrowing draws the very same value
 * and continues exactly as Immersive Weathering does; the seed is also written back before any other growth class runs and
 * at the end. Only when level.random is exactly a LegacyRandomSource and neither LegacyRandomSource nor BitRandomSource
 * carries another mod's mixin (Hostile Villages 5.7, for one, redirects next's compare-and-set so that a conflicting draw
 * no longer throws: then the batch stands aside and every roll draws as shipped). The one internal difference: one
 * compare-and-set per run of rolls instead of one per roll (a write-back that finds another thread's write throws
 * ThreadingDetector's exception, as LegacyRandomSource itself does on a conflicting draw).
 *
 * -Dbons_and_furious.immersiveweatheringGrowthTable=false / -Dbons_and_furious.immersiveweatheringGrowthRollBatch=false
 * switch them off at run time.
 * -Dbons_and_furious.immersiveweatheringGrowthTable.shadow=true: every call also walks Immersive Weathering's live sets and
 * compares the remaining growths with the table's (SHADOW_CHECKS / SHADOW_MISMATCHES, WARN for the first 20).
 */
public final class GrowthTable {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.immersiveweatheringGrowthTable", "true"));
    public static volatile boolean rollBatch = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.immersiveweatheringGrowthRollBatch", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.immersiveweatheringGrowthTable.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    private static final IBlockGrowth[] EMPTY = new IBlockGrowth[0];
    private static volatile boolean announcedTable, announcedBatch;

    /** One tick source's table, built from the map objects it names (immutable once published). */
    private static final class Table {
        final Object universalRef, blockRef;
        final IBlockGrowth[] universal;
        final IdentityHashMap<Block, IBlockGrowth[]> perBlock;

        Table(Object universalRef, Object blockRef, IBlockGrowth[] universal, IdentityHashMap<Block, IBlockGrowth[]> perBlock) {
            this.universalRef = universalRef;
            this.blockRef = blockRef;
            this.universal = universal;
            this.perBlock = perBlock;
        }
    }

    private static final Table[] TABLES = new Table[TickSource.values().length];

    private GrowthTable() {}

    /** The growths of a set that can have an effect, in the set's iteration order. */
    static IBlockGrowth[] remaining(Set<IBlockGrowth> set) {
        if (set == null || set.isEmpty()) return EMPTY;
        List<IBlockGrowth> out = new ArrayList<>(set.size());
        for (IBlockGrowth g : set) {
            if (g != null && g.getClass() == NoOpBlockGrowth.class) continue;
            if (g != null && g.getClass() == ConfigurableBlockGrowth.class && ((ConfigurableBlockGrowth) g).getGrowthChance() == 0.0f) continue;
            out.add(g);
        }
        return out.isEmpty() ? EMPTY : out.toArray(EMPTY);
    }

    private static Table table(int ordinal, Set<IBlockGrowth> universal, Map<Block, Set<IBlockGrowth>> perBlock) {
        Table t = TABLES[ordinal];
        if (t != null && t.universalRef == universal && t.blockRef == perBlock) return t;
        IdentityHashMap<Block, IBlockGrowth[]> map = new IdentityHashMap<>();
        if (perBlock != null) {
            for (Map.Entry<Block, Set<IBlockGrowth>> e : perBlock.entrySet()) {
                IBlockGrowth[] a = remaining(e.getValue());
                if (a.length > 0) map.put(e.getKey(), a);
            }
        }
        t = new Table(universal, perBlock, remaining(universal), map);
        TABLES[ordinal] = t;
        if (!announcedTable) {
            announcedTable = true;
            LOGGER.info("Bons and Furious: immersiveweathering_growth_table applies (Immersive Weathering's block growths come from a table without no-op entries){}",
                    SHADOW ? " - shadow verification on" : "");
        }
        return t;
    }

    /**
     * tickBlock's body with the table (and the roll batch when that switch is on). Returns false, before doing anything,
     * when Immersive Weathering's own body must run (an argument it handles itself); true when the call is done.
     */
    public static boolean tick(TickSource source, BlockState state, ServerLevel level, BlockPos pos,
                               Set<IBlockGrowth> universal, Map<Block, Set<IBlockGrowth>> perBlock) {
        if (source == null || state == null) return false;
        Table t = table(source.ordinal(), universal, perBlock);
        if (!CommonConfigs.BLOCK_GROWTHS.get()) return true;
        Block block = state.m_60734_();
        if (block instanceof IConditionalGrowingBlock cb && !cb.canGrow(state)) return true;
        IBlockGrowth[] own = t.perBlock.get(block);
        IBlockGrowth[] uni = t.universal;
        if (SHADOW) shadow(uni, own, universal, perBlock, block);
        if (uni.length == 0 && own == null) return true;
        Supplier<Holder<Biome>> biome = Suppliers.memoize(() -> level.m_204166_(pos));
        if (uni.length > 0) run(uni, pos, state, level, biome);
        if (own != null) run(own, pos, state, level, biome);
        return true;
    }

    /**
     * True when no mixin other than ours changed a class (no @MixinMerged member from another mixin). The roll batch
     * replays LegacyRandomSource.next and BitRandomSource.nextFloat as shipped; a pack where another mod changes them (for
     * example a redirect of next's compare-and-set) keeps the per-roll draws.
     */
    public static boolean untouched(Class<?> type) {
        return UNTOUCHED.get(type);
    }

    private static final ClassValue<Boolean> UNTOUCHED = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            for (java.lang.reflect.Method m : type.getDeclaredMethods()) {
                org.spongepowered.asm.mixin.transformer.meta.MixinMerged merged = m.getAnnotation(org.spongepowered.asm.mixin.transformer.meta.MixinMerged.class);
                if (merged != null && !merged.mixin().startsWith("bons.furious.")) {
                    LOGGER.info("Bons and Furious: immersiveweathering_growth_roll_batch stands aside ({} is changed by {}); growth chances keep one draw each",
                            type.getSimpleName(), merged.mixin());
                    return false;
                }
            }
            return true;
        }
    };

    private static AtomicLong seedOf(ServerLevel level) {
        RandomSource r = level.f_46441_;
        if (r != null && r.getClass() == LegacyRandomSource.class && r instanceof SeedAccess sa
                && untouched(LegacyRandomSource.class) && untouched(net.minecraft.world.level.levelgen.BitRandomSource.class)) return sa.bons$iwC49Seed();
        return null;
    }

    private static void run(IBlockGrowth[] growths, BlockPos pos, BlockState state, ServerLevel level, Supplier<Holder<Biome>> biome) {
        AtomicLong seed = rollBatch ? seedOf(level) : null;
        if (seed == null) {
            for (IBlockGrowth g : growths) g.tryGrowing(pos, state, level, biome);
            return;
        }
        long read = seed.get(), s = read;
        for (IBlockGrowth g : growths) {
            if (g != null && g.getClass() == ConfigurableBlockGrowth.class) {
                float chance = ((ConfigurableBlockGrowth) g).getGrowthChance();
                if (chance == 0.0f) continue;
                long next = s * 25214903917L + 11L & 281474976710655L;
                if (!((float) (int) (next >> 24) * 5.9604645E-8F < chance)) {
                    s = next;                                   // canGrow's draw fails: all it would have done
                    continue;
                }
            }
            if (s != read) publish(seed, read, s);              // the growth draws from the seed as it stands now
            g.tryGrowing(pos, state, level, biome);
            read = s = seed.get();
        }
        if (s != read) {
            publish(seed, read, s);
            if (!announcedBatch) {
                announcedBatch = true;
                LOGGER.info("Bons and Furious: immersiveweathering_growth_roll_batch applies (Immersive Weathering's growth chances roll on a local copy of the world random's seed)");
            }
        }
    }

    private static void publish(AtomicLong seed, long read, long s) {
        if (!seed.compareAndSet(read, s)) throw ThreadingDetector.m_199417_("LegacyRandomSource", null);
    }

    private static void shadow(IBlockGrowth[] uni, IBlockGrowth[] own, Set<IBlockGrowth> universal, Map<Block, Set<IBlockGrowth>> perBlock, Block block) {
        SHADOW_CHECKS.incrementAndGet();
        IBlockGrowth[] liveUni = remaining(universal), liveOwn = remaining(perBlock == null ? null : perBlock.get(block));
        boolean same = java.util.Arrays.equals(liveUni, uni) && java.util.Arrays.equals(liveOwn, own == null ? EMPTY : own);
        if (!same) {
            long m = SHADOW_MISMATCHES.incrementAndGet();
            if (m <= 20) LOGGER.warn("Bons and Furious: immersiveweathering_growth_table shadow mismatch #{} for {}: live {} / {} growths, table {} / {}",
                    m, block, liveUni.length, liveOwn.length, uni.length, own == null ? 0 : own.length);
        }
    }
}
