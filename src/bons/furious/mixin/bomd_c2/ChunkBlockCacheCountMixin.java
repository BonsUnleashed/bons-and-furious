package bons.furious.mixin.bomd_c2;

import bons.furious.patch.bomd_c2.BlockCachePresence;
import bons.furious.patch.bomd_c2.TrackedBlockCounts;
import com.cerbon.bosses_of_mass_destruction.capability.ChunkBlockCache;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * bomd_block_cache_presence (Bosses of Mass Destruction 1.1.2, LGPL; both sides), part 1 of 4: BOMD's ChunkBlockCache
 * counts, per block type, the positions its sets hold. The only set mutations are the HashSet.add in addToChunk and the
 * HashSet.remove in removeFromChunk; their boolean results are observed (and passed on unchanged) and, when true (the
 * set changed), the block's count moves by one (here and in BlockCachePresence's sum over all caches). The cache's
 * behaviour is unchanged.
 */
@Mixin(value = ChunkBlockCache.class, remap = false)
public abstract class ChunkBlockCacheCountMixin implements TrackedBlockCounts {
    @Unique
    private final Reference2IntOpenHashMap<Block> bons$trackedCounts = new Reference2IntOpenHashMap<>();

    @Override
    public int bons$trackedCount(Block block) {
        return this.bons$trackedCounts.getInt(block);
    }

    @ModifyExpressionValue(method = "addToChunk", at = @At(value = "INVOKE", target = "Ljava/util/HashSet;add(Ljava/lang/Object;)Z"))
    private boolean bons$countAdded(boolean added, @Local(argsOnly = true) Block block) {
        if (added) {
            this.bons$trackedCounts.addTo(block, 1);
            BlockCachePresence.counted(block, 1);
        }
        return added;
    }

    @ModifyExpressionValue(method = "removeFromChunk", at = @At(value = "INVOKE", target = "Ljava/util/HashSet;remove(Ljava/lang/Object;)Z"))
    private boolean bons$countRemoved(boolean removed, @Local(argsOnly = true) Block block) {
        if (removed) {
            this.bons$trackedCounts.addTo(block, -1);
            BlockCachePresence.counted(block, -1);
        }
        return removed;
    }
}
