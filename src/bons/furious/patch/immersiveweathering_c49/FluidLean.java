package bons.furious.patch.immersiveweathering_c49;

import bons.furious.mixin.immersiveweathering_c49.AdjacentBlocksAccessor;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.mojang.datafixers.util.Pair;
import com.ordana.immersive_weathering.blocks.mossy.Mossable;
import com.ordana.immersive_weathering.data.fluid_generators.IFluidGenerator;
import com.ordana.immersive_weathering.data.fluid_generators.OtherFluidGenerator;
import com.ordana.immersive_weathering.data.fluid_generators.SelfFluidGenerator;
import com.ordana.immersive_weathering.data.fluid_generators.builtin.BurnMossGenerator;
import com.ordana.immersive_weathering.data.position_tests.IPositionRuleTest;
import com.ordana.immersive_weathering.util.Weatherable;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import net.mehvahdjukaar.moonlight.api.platform.PlatHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.RuleTest;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch immersiveweathering_fluid_generator_lean (Immersive Weathering 1.20.1-2.0.5, LGPLv3; server side).
 * Immersive Weathering's FluidGeneratorsHandler.generate and the tryGenerating / isMet / testFails of its three generator
 * classes are carried here in a modified form (LGPLv3 permits it).
 *
 * What Immersive Weathering does. Its LiquidBlock mixin (shouldSpreadLiquid) and its FluidInteractionRegistry mixin
 * (canInteract) run the fluid generators of the fluid for every lava and water spread check: 12 bundled lava generators,
 * one water generator. Each call allocates an EnumMap neighbour cache, and each generator a Guava memoizing biome
 * supplier, a capturing lambda per neighbour lookup (computeIfAbsent) and a new BlockPos per direction it looks at.
 *
 * What the switch does. The same generators run in the same order over a BlockState[6] neighbour cache (by Direction
 * ordinal, filled on first use like the EnumMap's computeIfAbsent), with the same RuleTest.test(state, level.random) calls
 * in the same order (so the same random draws), the same position-test calls with an equal BlockPos and a memoizing biome
 * supplier per generator call (created on first need: the biome is computed at most once per generator call, as before),
 * the same block placements and the same result. Only lists whose every generator is exactly SelfFluidGenerator (with
 * Immersive Weathering's own AdjacentBlocks), OtherFluidGenerator or BurnMossGenerator take this path; any other generator
 * class keeps Immersive Weathering's own body for the whole list. BlockPos values are built only where a block is read, a
 * position test runs or a block is placed (each an equal, immutable position).
 *
 * -Dbons_and_furious.immersiveweatheringFluidGeneratorLean=false switches it off at run time.
 */
public final class FluidLean {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.immersiveweatheringFluidGeneratorLean", "true"));
    private static final Direction[] ALL = Direction.values();
    private static volatile boolean announced;
    private static final Class<?> ADJACENT = SelfFluidGenerator.AdjacentBlocks.class;

    private FluidLean() {}

    /** True when every generator of the list is one of the three classes this twin covers. */
    static boolean covered(ImmutableList<IFluidGenerator> list) {
        for (IFluidGenerator g : list) {
            if (g == null) return false;
            Class<?> c = g.getClass();
            if (c == SelfFluidGenerator.class) {
                Object a = ((SelfFluidGenerator) g).getAdjacentBlocksCondition();
                if (a == null || a.getClass() != ADJACENT) return false;
            } else if (c != OtherFluidGenerator.class && c != BurnMossGenerator.class) {
                return false;
            }
        }
        return true;
    }

    /**
     * generate's body. Returns null, before doing anything, when Immersive Weathering's own body must run (an empty or
     * missing list, which it answers at once, or a generator class this twin does not cover).
     */
    public static Optional<Pair<BlockPos, SoundEvent>> generate(List<Direction> dirs, BlockPos pos, Level level, ImmutableList<IFluidGenerator> list) {
        if (list == null || list.isEmpty() || !covered(list)) return null;
        if (!PlatHelper.isAreaLoaded(level, pos, 3)) return Optional.empty();
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: immersiveweathering_fluid_generator_lean applies (Immersive Weathering's fluid generators run without per-check allocations)");
        }
        BlockState[] cache = new BlockState[6];
        for (IFluidGenerator g : list) {
            Optional<BlockPos> res;
            Class<?> c = g.getClass();
            if (c == SelfFluidGenerator.class) res = self((SelfFluidGenerator) g, dirs, pos, level, cache);
            else if (c == OtherFluidGenerator.class) res = other((OtherFluidGenerator) g, dirs, pos, level, cache);
            else res = burnMoss(dirs, pos, level, cache);
            if (res.isPresent()) return Optional.of(Pair.of(res.get(), g.getSound()));
        }
        return Optional.empty();
    }

    private static BlockState state(BlockState[] cache, Direction d, BlockPos pos, Level level) {
        int i = d.ordinal();
        BlockState s = cache[i];
        if (s == null) cache[i] = s = level.m_8055_(pos.m_121945_(d));
        return s;
    }

    private static Supplier<Holder<Biome>> biome(Level level, BlockPos pos) {
        return Suppliers.memoize(() -> level.m_204166_(pos));
    }

    private static Optional<BlockPos> self(SelfFluidGenerator g, List<Direction> dirs, BlockPos pos, Level level, BlockState[] cache) {
        if (!isMet((AdjacentBlocksAccessor) g.getAdjacentBlocksCondition(), dirs, pos, level, cache, g.getPositionTests())) return Optional.empty();
        if (pos != null) {
            level.m_46597_(pos, g.getGrowth());
            return Optional.of(pos);
        }
        return Optional.empty();
    }

    private static boolean isMet(AdjacentBlocksAccessor a, List<Direction> dirs, BlockPos pos, Level level, BlockState[] cache, Optional<IPositionRuleTest> extra) {
        Supplier<Holder<Biome>> biome = null;
        for (RuleTest r : a.bons$iwC49AnyBlocks()) {
            boolean found = false;
            for (Direction d : ALL) {
                BlockState state = state(cache, d, pos, level);
                if (!r.m_213865_(state, level.f_46441_)) continue;
                if (extra.isPresent()) {
                    if (biome == null) biome = biome(level, pos);
                    if (!extra.get().test(biome, pos.m_121945_(d), level)) continue;
                }
                found = true;
                break;
            }
            if (!found) return false;
        }
        for (RuleTest r : a.bons$iwC49SidesBlocks()) {
            boolean found = false;
            for (Direction d : dirs) {
                if (!d.m_122434_().m_122479_()) continue;
                BlockState state = state(cache, d, pos, level);
                if (!r.m_213865_(state, level.f_46441_)) continue;
                if (extra.isPresent()) {
                    if (biome == null) biome = biome(level, pos);
                    if (!extra.get().test(biome, pos.m_121945_(d), level)) continue;
                }
                found = true;
                break;
            }
            if (!found) return false;
        }
        RuleTest up = a.bons$iwC49UpBlock();
        if (up != null) {
            if (biome == null && extra.isPresent()) biome = biome(level, pos);
            if (fails(up, pos, level, cache, extra, biome, Direction.UP)) return false;
        }
        RuleTest down = a.bons$iwC49DownBlock();
        if (down == null) return true;
        if (biome == null && extra.isPresent()) biome = biome(level, pos);
        return !fails(down, pos, level, cache, extra, biome, Direction.DOWN);
    }

    private static boolean fails(RuleTest test, BlockPos pos, Level level, BlockState[] cache, Optional<IPositionRuleTest> extra, Supplier<Holder<Biome>> biome, Direction dir) {
        BlockState state = state(cache, dir, pos, level);
        if (!test.m_213865_(state, level.f_46441_)) return true;
        return extra.isPresent() && !extra.get().test(biome, pos.m_121945_(dir), level);
    }

    private static Optional<BlockPos> other(OtherFluidGenerator g, List<Direction> dirs, BlockPos pos, Level level, BlockState[] cache) {
        Supplier<Holder<Biome>> biome = null;
        RuleTest target = g.getTarget();
        Optional<IPositionRuleTest> extra = g.getExtraCheck();
        for (Direction d : dirs) {
            BlockState state = state(cache, d, pos, level);
            if (!target.m_213865_(state, level.f_46441_)) continue;
            BlockPos p = pos.m_121945_(d);
            if (extra.isPresent()) {
                if (biome == null) biome = biome(level, pos);
                if (!extra.get().test(biome, p, level)) continue;
            }
            level.m_46597_(p, g.getGrowth());
            return Optional.of(p);
        }
        return Optional.empty();
    }

    private static Optional<BlockPos> burnMoss(List<Direction> dirs, BlockPos pos, Level level, BlockState[] cache) {
        for (Direction d : dirs) {
            BlockState state = state(cache, d, pos, level);
            BlockState s = Mossable.getUnaffectedMossBlock(state);
            if (s == state) continue;
            BlockPos p = pos.m_121945_(d);
            level.m_46597_(p, s.m_61124_(Mossable.WEATHERABLE, Weatherable.WeatheringState.STABLE));
            return Optional.of(p);
        }
        return Optional.empty();
    }
}
