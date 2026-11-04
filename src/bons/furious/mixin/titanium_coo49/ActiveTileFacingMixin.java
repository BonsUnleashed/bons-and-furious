package bons.furious.mixin.titanium_coo49;

import bons.furious.patch.titanium_coo49.TitaniumFacing;
import com.hrznstudio.titanium.block.tile.ActiveTile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * titanium_facing_single_state_read (Titanium 3.8.35, both sides; exact; no Titanium code is carried).
 * ActiveTile.getFacingDirection answers from one read of the machine's block state instead of up to three reads of the same
 * position within the same call. See TitaniumFacing.
 */
@Mixin(value = ActiveTile.class, remap = false)
public abstract class ActiveTileFacingMixin extends BlockEntity {
    protected ActiveTileFacingMixin(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Inject(method = "getFacingDirection()Lnet/minecraft/core/Direction;", at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    private void bons$titaniumFacingSingleRead(CallbackInfoReturnable<Direction> cir) {
        if (TitaniumFacing.enabled) cir.setReturnValue(TitaniumFacing.facing(this.f_58857_, this.f_58858_));
    }
}
