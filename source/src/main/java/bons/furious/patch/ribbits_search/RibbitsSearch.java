package bons.furious.patch.ribbits_search;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.logging.LogUtils;
import com.yungnickyoung.minecraft.ribbits.entity.RibbitEntity;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import org.slf4j.Logger;

/**
 * Bons and Furious switch ribbits_goal_search_sections (since 1.0.45; Ribbits 3.0.2 on Minecraft 1.20.1 / Forge 47.4.16;
 * server side). SRG names for Minecraft members.
 *
 * What Ribbits does. The gardener's RibbitWaterCropsGoal.canUse (canUse) and the fisherman's RibbitFishGoal.canUse, polled
 * every other tick in daytime for every such ribbit, call BlockPos.findClosestMatch(pos, 16, 5, test): up to 11,979 positions
 * in withinManhattan order, each a block read plus a test. Crops: "the block is a CropBlock ? (buff cooldown == 0 && the crop
 * is not at max age) : false" (the cooldown is set to 1,200 ticks after each watering, so for most of the day every position
 * fails). Fish: "the fluid is water && the block above is air".
 *
 * What the switch does. It visits the same positions in the same order with the same chunk lookups (Level.getChunk(x, z), the
 * call getBlockState / getFluidState start with; a lookup that repeats the previous one with nothing in between is dropped),
 * and skips the read and the test at positions where the test is a pure false: in a chunk section whose palette holds no
 * CropBlock state (crops) or no state whose fluid is in #water (fish), and, for crops, everywhere while the ribbit's buff
 * cooldown is not 0 (the cooldown, a plain field, is read again after every chunk lookup, the only other code that runs
 * between skipped positions; once it reads 0 the section rule takes over). Elsewhere the goal's own test runs; the answer is
 * its first hit, returned as findClosestMatch returns it (Optional of the search's MutableBlockPos).
 *
 * The visiting order and the section verdicts are the vanilla_search group's machinery (NonPoiSearch.diamond, the
 * withinManhattan order; NonPoiSearch.Verdicts, palette verdicts cached per palette object and size for one search), called
 * through method handles because that group keeps them package-private. Applies only when the whole search box lies inside
 * the build height (vanilla's getBlockState skips the chunk lookup outside it, Radium's does not), the level is not a debug
 * world, and the level's class keeps Level's getBlockState, getFluidState and chunk lookups. Assumption, as for
 * vanilla_repellent_search_sections: a chunk lookup that repeats the previous one with nothing in between has no effect beyond
 * returning the same chunk.
 *
 * -Dbons_and_furious.ribbitsGoalSearchSections=false runs the original searches.
 * -Dbons_and_furious.ribbitsGoalSearchSections.shadow=true (verification) runs both and compares; the original's answer is
 * used (SHADOW_CHECKS / SHADOW_MISMATCHES, the first 20 logged).
 */
public final class RibbitsSearch {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.ribbitsGoalSearchSections", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.ribbitsGoalSearchSections.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    private static final long NO_KEY = Long.MIN_VALUE;
    private static volatile boolean announced;

    /** "Could the crops test do anything but a pure false here?" - a CropBlock state (its isMaxAge may be another mod's code). */
    static final Predicate<BlockState> CROP = s -> s.getBlock() instanceof CropBlock;
    /** "Could the fish test pass here?" - a state whose fluid is in #water. */
    static final Predicate<BlockState> WATER = s -> s.getFluidState().is(FluidTags.WATER);

    // ---- the vanilla_search machinery (bons.furious.patch.vanilla_search.NonPoiSearch), resolved once
    private static final MethodHandle DIAMOND, DX, DY, DZ, VERDICTS_NEW, COLUMN, VERDICT, SEC_MIN;
    private static final byte SKIP;
    /** True when the machinery was found and NonPoiSearch found the 1.20.1 palette layout. */
    static final boolean READY;

    static {
        MethodHandle diamond = null, dx = null, dy = null, dz = null, ctor = null, column = null, verdict = null, secMin = null;
        byte skip = 0;
        boolean ready = false;
        try {
            ClassLoader cl = RibbitsSearch.class.getClassLoader();
            Class<?> nps = Class.forName("bons.furious.patch.vanilla_search.NonPoiSearch", true, cl);
            Class<?> ver = Class.forName("bons.furious.patch.vanilla_search.NonPoiSearch$Verdicts", true, cl);
            MethodHandles.Lookup lk = MethodHandles.privateLookupIn(nps, MethodHandles.lookup());
            diamond = lk.findStatic(nps, "diamond", MethodType.methodType(int[].class, int.class, int.class, int.class));
            dx = lk.findStatic(nps, "dx", MethodType.methodType(int.class, int.class));
            dy = lk.findStatic(nps, "dy", MethodType.methodType(int.class, int.class));
            dz = lk.findStatic(nps, "dz", MethodType.methodType(int.class, int.class));
            Field s = nps.getDeclaredField("SKIP");
            s.setAccessible(true);
            skip = s.getByte(null);
            Field r = nps.getDeclaredField("READY");
            r.setAccessible(true);
            MethodHandles.Lookup lv = MethodHandles.privateLookupIn(ver, MethodHandles.lookup());
            ctor = lv.findConstructor(ver, MethodType.methodType(void.class, int.class, int.class, int.class, int.class, int.class, int.class,
                    Predicate.class, BlockState.class)).asType(MethodType.methodType(Object.class, int.class, int.class, int.class, int.class,
                    int.class, int.class, Predicate.class, BlockState.class));
            column = lv.findVirtual(ver, "column", MethodType.methodType(int.class, int.class, int.class))
                    .asType(MethodType.methodType(int.class, Object.class, int.class, int.class));
            verdict = lv.findVirtual(ver, "verdict", MethodType.methodType(byte.class, ChunkAccess.class, int.class, int.class))
                    .asType(MethodType.methodType(byte.class, Object.class, ChunkAccess.class, int.class, int.class));
            secMin = lv.findGetter(ver, "secMin", int.class).asType(MethodType.methodType(int.class, Object.class));
            ready = r.getBoolean(null);
            if (!ready) LOGGER.warn("Bons and Furious: ribbits_goal_search_sections is inactive because the vanilla_search palette machinery is inactive");
        } catch (Throwable t) {
            LOGGER.warn("Bons and Furious: ribbits_goal_search_sections is inactive: the vanilla_search machinery it uses was not found ({})", t.toString());
        }
        DIAMOND = diamond;
        DX = dx;
        DY = dy;
        DZ = dz;
        VERDICTS_NEW = ctor;
        COLUMN = column;
        VERDICT = verdict;
        SEC_MIN = secMin;
        SKIP = skip;
        READY = ready;
    }

    /**
     * True when the level reads blocks, fluids and chunks through Level's own code. A level class that declares one of the four
     * methods keeps the original searches, with one INFO line naming it (1.0.45). Synthetic bridges do not count: six mods of
     * this pack merge a javac bridge "ChunkAccess getChunk(int, int)" (body: return super.getChunk(x, z), the LevelChunk
     * method) into ServerLevel, which kept this switch off on every server level before; the lookups replayed here and the
     * reads skipped here call the LevelChunk method itself (see FireIdleScan's class comment, same rule).
     */
    private static final ClassValue<Boolean> LEVEL_OK = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            for (Class<?> c = type; c != null && c != Level.class; c = c.getSuperclass()) {
                String which = declares(c, "getBlockState", BlockPos.class) ? "getBlockState(BlockPos) [getBlockState]"
                        : declares(c, "getFluidState", BlockPos.class) ? "getFluidState(BlockPos) [getFluidState]"
                        : declares(c, "getChunk", int.class, int.class) ? "getChunk(int, int) [getChunk]"
                        : declares(c, "getChunkAt", BlockPos.class) ? "getChunkAt(BlockPos) [getChunkAt]" : null;
                if (which != null) {
                    if (STAND_DOWN_LINES.incrementAndGet() <= 8)
                        LOGGER.info("Bons and Furious: ribbits_goal_search_sections steps aside for level class {}: {} overrides {}", type.getName(), c.getName(), which);
                    return false;
                }
            }
            return true;
        }
    };

    private static final java.util.concurrent.atomic.AtomicInteger STAND_DOWN_LINES = new java.util.concurrent.atomic.AtomicInteger();

    private RibbitsSearch() {
    }

    /**
     * Whether c itself declares the method; an inspection failure counts as declared. A synthetic bridge does not count unless c
     * also declares a non-bridge method of that name and parameter count, the covariant or generic override it forwards to. A
     * bridge alone forwards to the inherited method.
     */
    static boolean declares(Class<?> c, String name, Class<?>... params) {
        try {
            if (!c.getDeclaredMethod(name, params).isBridge()) return true;
            for (java.lang.reflect.Method m : c.getDeclaredMethods()) {
                if (!m.isBridge() && m.getName().equals(name) && m.getParameterCount() == params.length) return true;
            }
            return false;
        } catch (NoSuchMethodException e) {
            return false;
        } catch (Throwable t) {
            return true;
        }
    }

    /** RibbitWaterCropsGoal.canUse: BlockPos.findClosestMatch(center, w, h, test), wrapped. */
    public static Optional<BlockPos> crops(RibbitEntity ribbit, BlockPos center, int w, int h, Predicate<BlockPos> test, Operation<Optional<BlockPos>> original) {
        return find(ribbit, center, w, h, test, CROP, true, original);
    }

    /** RibbitFishGoal.canUse: BlockPos.findClosestMatch(center, w, h, test), wrapped. */
    public static Optional<BlockPos> fish(RibbitEntity ribbit, BlockPos center, int w, int h, Predicate<BlockPos> test, Operation<Optional<BlockPos>> original) {
        return find(ribbit, center, w, h, test, WATER, false, original);
    }

    private static Optional<BlockPos> find(RibbitEntity ribbit, BlockPos center, int w, int h, Predicate<BlockPos> test, Predicate<BlockState> interesting,
                                           boolean cooldownRule, Operation<Optional<BlockPos>> original) {
        Level level = ribbit.level();
        if (!enabled || !READY || level == null || level.isDebug() || w < 0 || h < 0 || w > 256 || h > 256 || !LEVEL_OK.get(level.getClass()))
            return original.call(center, w, h, test);
        int cy = center.getY();
        if (cy - h < level.getMinBuildHeight() || cy + h >= level.getMaxBuildHeight()) return original.call(center, w, h, test);
        BlockPos.MutableBlockPos ours;
        try {
            ours = search(level, center.getX(), cy, center.getZ(), w, h, test, interesting, cooldownRule ? ribbit : null);
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable t) {
            throw new IllegalStateException(t);
        }
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: ribbits_goal_search_sections applies (gardener and fisherman ribbits skip block reads where their search cannot match){}",
                    SHADOW ? " - shadow verification on" : "");
        }
        if (SHADOW) {
            Optional<BlockPos> theirs = original.call(center, w, h, test);
            SHADOW_CHECKS.incrementAndGet();
            boolean same = ours == null ? theirs.isEmpty() : theirs.isPresent() && ours.equals(theirs.get()) && ours.getClass() == theirs.get().getClass();
            if (!same) {
                long m = SHADOW_MISMATCHES.incrementAndGet();
                if (m <= 20) LOGGER.warn("Bons and Furious: ribbits_goal_search_sections shadow mismatch #{}: the switch found {} where the original found {}",
                        m, ours, theirs.orElse(null));
            }
            return theirs;
        }
        return ours == null ? Optional.empty() : Optional.of(ours);
    }

    static BlockPos.MutableBlockPos search(Level level, int cx, int cy, int cz, int w, int h, Predicate<BlockPos> test, Predicate<BlockState> interesting,
                                           RibbitEntity cooldownOf) throws Throwable {
        int[] order = (int[]) DIAMOND.invokeExact(w, h, w);
        Object verdicts = VERDICTS_NEW.invokeExact(SectionPos.blockToSectionCoord(cx - w), SectionPos.blockToSectionCoord(cx + w), SectionPos.blockToSectionCoord(cz - w),
                SectionPos.blockToSectionCoord(cz + w), level.getSectionIndex(cy - h), level.getSectionIndex(cy + h), interesting, Blocks.AIR.defaultBlockState());
        int secMin = (int) SEC_MIN.invokeExact(verdicts);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        long lastKey = NO_KEY;
        LevelChunk chunk = null;
        int column = 0;
        int minSection = level.getMinSection();                                  // getSectionIndex(y) = (y >> 4) - minSection
        boolean cool = cooldownOf != null && cooldownOf.getBuffCooldown() != 0;
        for (int packed : order) {
            int x = cx + (int) DX.invokeExact(packed), y = cy + (int) DY.invokeExact(packed), z = cz + (int) DZ.invokeExact(packed);
            int chunkX = x >> 4, chunkZ = z >> 4;
            long key = (long) chunkX & 0xFFFFFFFFL | ((long) chunkZ & 0xFFFFFFFFL) << 32;
            if (key != lastKey) {
                chunk = level.getChunk(chunkX, chunkZ);                       // the lookup getBlockState / getFluidState make for this position
                lastKey = key;
                column = (int) COLUMN.invokeExact(verdicts, chunkX, chunkZ);
                if (cool) cool = cooldownOf.getBuffCooldown() != 0;           // the only code that ran since the last read
            }
            if (cool) continue;                                              // crops while the buff cooldown runs: the test is false
            int si = (y >> 4) - minSection;
            if ((byte) VERDICT.invokeExact(verdicts, (ChunkAccess) chunk, column + (si - secMin), si) == SKIP) continue;
            if (test.test(cursor.set(x, y, z))) return cursor;
            lastKey = NO_KEY;                                                // the goal's own test ran: look the chunk up again
        }
        return null;
    }
}
