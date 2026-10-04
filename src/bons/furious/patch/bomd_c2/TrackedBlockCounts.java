package bons.furious.patch.bomd_c2;

import net.minecraft.world.level.block.Block;

/**
 * Bons and Furious switch bomd_block_cache_presence: implemented on BOMD's ChunkBlockCache by ChunkBlockCacheCountMixin.
 * The number of positions the cache holds for a block type, over all its chunks (0 for a type it never held).
 */
public interface TrackedBlockCounts {
    int bons$trackedCount(Block block);
}
