package bons.furious.patch.vanilla_search;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * Bons and Furious switch vanilla_repellent_search_sections (Minecraft 1.21.1 with NeoForge 21.1.252; server side,
 * including the integrated server). Mojang member names.
 *
 * Every piglin and hoglin sensor run (PiglinSpecificSensor / HoglinSpecificSensor findNearestRepellent, about once a
 * second per mob) looks for the nearest repellent block with BlockPos.findClosestMatch(pos, 8, 4, test): 2,601 positions
 * in withinManhattan order, each a level.getBlockState plus a block-tag test (piglin_repellents, lit soul campfires;
 * hoglin_repellents).
 *
 * The switch visits the same positions in the same order and makes the same chunk lookups (Level.getChunk(x, z), the
 * call Level.getBlockState starts with in vanilla and in Radium's inlined copy; a lookup that repeats the previous one
 * with nothing in between is dropped, it can only hit the chunk cache), but it skips the block read and the test at
 * positions whose chunk section holds no state of the repellent tag (palette test): there the test is a pure false.
 * Positions in sections that may hold one run the sensor's own test; the answer is that test's first hit, returned as
 * the original returns it (Optional.of the search's MutableBlockPos).
 *
 * Applies only when the whole search box lies inside the build height (vanilla's getBlockState skips the chunk lookup
 * outside it, Radium's does not), the level is not a debug world, and the level's class keeps Level's getBlockState
 * and chunk lookups (getBlockState, getChunk, getChunkAt). Assumption as for vanilla_turtle_egg_search_sections: a chunk
 * lookup that repeats the previous one with nothing in between has no effect beyond returning the same chunk.
 *
 * Ported to 1.21.1: nothing in the searched code changed (both sensors' findNearestRepellent and their lambdas,
 * isValidRepellent, BlockPos.findClosestMatch / withinManhattan, Level.getBlockState / getChunk / getChunkAt,
 * LevelChunk.getBlockState and the palettes read the same); only the anonymous class of withinManhattan's iterator is
 * BlockPos$3 in 1.21.1 (BlockPos$1 is now the STREAM_CODEC, BlockPos$2 randomBetweenClosed's iterator), which the
 * guards name. Radium 0.13.1's inline_block_access copy of Level.getBlockState still starts with getChunk(x, z) and
 * answers AIR for hasOnlyAir sections, as in 1.20.1.
 *
 * -Dbons_and_furious.repellentSearchSections=false runs the original search.
 * -Dbons_and_furious.repellentSearchSections.shadow=true (verification) runs both and compares; the original's answer
 * is used (SHADOW_CHECKS / SHADOW_MISMATCHES, first 20 logged).
 */
public final class RepellentSearch {
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.repellentSearchSections", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.repellentSearchSections.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    public static final AtomicLong FAST = new AtomicLong();
    private static final long NO_KEY = Long.MIN_VALUE;
    private static volatile boolean announced;

    private static final ClassValue<Boolean> LEVEL_OK = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            for (Class<?> c = type; c != null && c != Level.class; c = c.getSuperclass()) {
                if (TurtleEggSearch.declares(c, "getBlockState", BlockPos.class) || TurtleEggSearch.declares(c, "getChunk", int.class, int.class)
                        || TurtleEggSearch.declares(c, "getChunkAt", BlockPos.class)) return false;
            }
            return true;
        }
    };

    private RepellentSearch() {
    }

    /**
     * In place of a repellent sensor's findNearestRepellent(level, entity): BlockPos.findClosestMatch(entity.blockPosition(),
     * 8, 4, test) where test is the sensor's own per-position test and tag the sensor's repellent tag.
     */
    public static Optional<BlockPos> find(ServerLevel level, LivingEntity entity, TagKey<Block> tag, Predicate<BlockPos> test,
                                          Operation<Optional<BlockPos>> original) {
        if (!enabled || !NonPoiSearch.READY || level == null || level.isDebug() || !LEVEL_OK.get(level.getClass())) {
            return original.call(level, entity);
        }
        int w = 8, h = 4;
        BlockPos center = entity.blockPosition();
        int cx = center.getX(), cy = center.getY(), cz = center.getZ();
        if (cy - h < level.getMinBuildHeight() || cy + h >= level.getMaxBuildHeight()) return original.call(level, entity);
        BlockPos.MutableBlockPos ours = search(level, cx, cy, cz, w, h, test, tag);
        FAST.incrementAndGet();
        if (!announced) {
            announced = true;
            NonPoiSearch.LOGGER.info("Bons and Furious: vanilla_repellent_search_sections applies (piglin and hoglin repellent searches skip block reads in sections without a repellent){}",
                    SHADOW ? " - shadow verification on" : "");
        }
        if (SHADOW) {
            Optional<BlockPos> theirs = original.call(level, entity);
            SHADOW_CHECKS.incrementAndGet();
            boolean same = ours == null ? theirs.isEmpty() : theirs.isPresent() && ours.equals(theirs.get()) && ours.getClass() == theirs.get().getClass();
            if (!same) {
                long m = SHADOW_MISMATCHES.incrementAndGet();
                if (m <= 20) NonPoiSearch.LOGGER.warn("Bons and Furious: vanilla_repellent_search_sections shadow mismatch #{}: the switch found {} where the original found {}",
                        m, ours, theirs.orElse(null));
            }
            return theirs;
        }
        return ours == null ? Optional.empty() : Optional.of(ours);
    }

    static BlockPos.MutableBlockPos search(ServerLevel level, int cx, int cy, int cz, int w, int h, Predicate<BlockPos> test, TagKey<Block> tag) {
        int[] order = NonPoiSearch.diamond(w, h, w);
        NonPoiSearch.Verdicts verdicts = new NonPoiSearch.Verdicts(SectionPos.blockToSectionCoord(cx - w), SectionPos.blockToSectionCoord(cx + w),
                SectionPos.blockToSectionCoord(cz - w), SectionPos.blockToSectionCoord(cz + w), level.getSectionIndex(cy - h), level.getSectionIndex(cy + h),
                s -> s.is(tag), Blocks.AIR.defaultBlockState());
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        long lastKey = NO_KEY;
        LevelChunk chunk = null;
        int column = 0;
        int minSection = level.getMinSection();                              // getSectionIndex(y) = (y >> 4) - minSection
        for (int packed : order) {
            int x = cx + NonPoiSearch.dx(packed), y = cy + NonPoiSearch.dy(packed), z = cz + NonPoiSearch.dz(packed);
            int chunkX = x >> 4, chunkZ = z >> 4;
            long key = (long) chunkX & 0xFFFFFFFFL | ((long) chunkZ & 0xFFFFFFFFL) << 32;
            if (key != lastKey) {
                chunk = level.getChunk(chunkX, chunkZ);                 // the lookup getBlockState makes for this position
                lastKey = key;
                column = verdicts.column(chunkX, chunkZ);
            }
            int si = (y >> 4) - minSection;
            if (verdicts.verdict(chunk, column + (si - verdicts.secMin), si) == NonPoiSearch.SKIP) continue;
            if (test.test(cursor.set(x, y, z))) return cursor;
            lastKey = NO_KEY;                                           // the sensor's own test ran: look the chunk up again
        }
        return null;
    }
}
