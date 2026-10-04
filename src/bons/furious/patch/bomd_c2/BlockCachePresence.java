package bons.furious.patch.bomd_c2;

import com.cerbon.bosses_of_mass_destruction.block.BMDBlocks;
import com.cerbon.bosses_of_mass_destruction.capability.ChunkBlockCache;
import com.cerbon.bosses_of_mass_destruction.capability.util.BMDCapabilities;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.RegistryObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch bomd_block_cache_presence (Bosses of Mass Destruction 1.1.2, LGPL; both sides, the gates act on
 * the server).
 *
 * BOMD keeps, per level, a ChunkBlockCache (chunk -> block type -> positions) of its mob wards, monoliths and levitation
 * blocks, filled and emptied only by ChunkCacheBlockEntity (addToChunk on its first tick, removeFromChunk in setRemoved,
 * which also runs for every block entity of an unloading chunk). Three scans read it:
 *  - MobWardBlock.canSpawn, from BOMD's hook at the return of NaturalSpawner.isValidSpawnPostitionForType: 9 x 9 chunks
 *    of lookups (new ChunkPos and a list copy each) for every spawn position that passed the vanilla checks;
 *  - MonolithBlock.getExplosionPower, from BOMD's hook at the head of ServerLevel.explode: the same 81-chunk walk;
 *  - LevitationBlockEntity.tickFlight, for every server player every tick: 9 BlockPos, two streams and a set.
 * Each scan can only find something if the cache holds a position for that block type. The cache's sets only change
 * through the HashSet.add in addToChunk and the HashSet.remove in removeFromChunk (the map is private, getBlocksFromChunk
 * returns copies); ChunkBlockCacheCountMixin counts every add and remove that changed a set, per cache and per block
 * type (TrackedBlockCounts), and here summed over all caches of both sides. A count of 0 means every set for that block
 * is empty, so the scan finds nothing: canSpawn leaves the spawn allowed, getExplosionPower returns the power unchanged,
 * tickFlight sees no levitation block. Then the scan is skipped (tickFlight runs only its "no block" tail). A global
 * count above 0 (a block placed anywhere, also in a client's cache) falls back to the level's own count; above 0 there,
 * BOMD's scan runs unchanged.
 * What is skipped has no other effect than building and discarding objects, except that the level's cache, created
 * lazily on the first capability lookup, may then be created later (it is empty either way).
 */
public final class BlockCachePresence {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    /** Runtime switch. -Dbons_and_furious.bomdBlockCachePresence=false runs every BOMD scan. */
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.bomdBlockCachePresence", "true"));
    private static final ConcurrentHashMap<Block, AtomicInteger> GLOBAL = new ConcurrentHashMap<>();
    private static volatile Block mobWard, monolith, levitation;
    private static volatile boolean announced;

    private BlockCachePresence() {
    }

    /** ChunkBlockCacheCountMixin: a cache's set for this block gained (+1) or lost (-1) a position. */
    public static void counted(Block block, int delta) {
        GLOBAL.computeIfAbsent(block, b -> new AtomicInteger()).addAndGet(delta);
    }

    public static int globalCount(Block block) {
        AtomicInteger c = GLOBAL.get(block);
        return c == null ? 0 : c.get();
    }

    /** True when the level's BOMD cache holds no position of this block type (BOMD's scan for it would find nothing). */
    public static boolean noneTracked(Level level, Block block) {
        if (!enabled || block == null) return false;
        if (globalCount(block) != 0) {
            Optional<ChunkBlockCache> cache = BMDCapabilities.getChunkBlockCache(level);
            if (cache.isPresent() && ((TrackedBlockCounts) cache.get()).bons$trackedCount(block) != 0) return false;
        }
        if (!announced) announce();
        return true;
    }

    public static boolean noMobWard(Level level) {
        Block b = mobWard;
        if (b == null) mobWard = b = resolve(BMDBlocks.MOB_WARD);
        return noneTracked(level, b);
    }

    public static boolean noMonolith(Level level) {
        Block b = monolith;
        if (b == null) monolith = b = resolve(BMDBlocks.MONOLITH_BLOCK);
        return noneTracked(level, b);
    }

    public static boolean noLevitationBlock(Level level) {
        Block b = levitation;
        if (b == null) levitation = b = resolve(BMDBlocks.LEVITATION_BLOCK);
        return noneTracked(level, b);
    }

    /** The registered block, or null while it is not available (then BOMD's own code runs and decides). */
    private static Block resolve(RegistryObject<Block> entry) {
        try {
            return entry.get();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static synchronized void announce() {
        if (announced) return;
        announced = true;
        LOGGER.info("Bons and Furious: bomd_block_cache_presence: BOMD's ward, monolith and levitation scans are skipped while no such block is tracked");
    }
}
