package bons.furious.mixin.spawn;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.ninni.spawn.server.level.feature.ZombifiedFlowerFeature;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * spawn_zombified_flower_floor (Spawn 4.0.7).
 *
 * ZombifiedFlowerFeature.place walks down each candidate column "while the block is replaceable" to find the ground,
 * with no lower limit. Inside Distant Horizons' generation region every block below the loaded terrain reads as air,
 * so the walk ran on for about a million blocks until DH's bounds check threw and aborted the chunk's feature step.
 * The walk now also stops at the level's minimum build height; in normal terrain the ground stops it first, so the
 * placed flowers are the same.
 */
@Mixin(value = ZombifiedFlowerFeature.class, remap = false)
public abstract class ZombifiedFlowerFloorMixin {
    /**
     * The walk's loop condition, {@code level.getBlockState(pos).canBeReplaced()} (the first canBeReplaced call in
     * place), becomes {@code canBeReplaced() && pos.getY() > level.getMinBuildHeight()}.
     */
    @ModifyExpressionValue(method = "m_142674_", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/state/BlockState;m_247087_()Z", ordinal = 0))
    private boolean bons$stopAtWorldFloor(boolean replaceable, @Local BlockPos.MutableBlockPos pos, @Local WorldGenLevel level) {
        return replaceable && pos.m_123342_() > level.m_141937_();   // getY() > getMinBuildHeight()
    }
}
