package bons.furious.mixin.climate_last_result;

import bons.furious.patch.climate_last_result.WeakLastResult;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * vanilla_climate_last_result_weak (Minecraft 1.20.1, both sides). [FIX]
 *
 * Climate.RTree's constructor creates the per-thread "last leaf" slot of the tree (new ThreadLocal, stored in the final
 * field f_186911_). The slot it creates is now a WeakLastResult, a ThreadLocal that keeps the leaf weakly (see there): the
 * search code (m_186930_) is unchanged and calls get and set on it as before. Climate.RTree is not public, hence the
 * string target. The ThreadLocal vanilla creates is discarded unused (one small object per tree; trees are built when a
 * world's biome source is created).
 */
@Mixin(targets = "net.minecraft.world.level.biome.Climate$RTree", remap = false)
public abstract class ClimateLastResultMixin {
    @ModifyExpressionValue(method = "<init>", at = @At(value = "NEW", target = "()Ljava/lang/ThreadLocal;"))
    private ThreadLocal<?> bons$weakLastResult(ThreadLocal<?> vanilla) {
        return WeakLastResult.slot(vanilla);
    }
}
