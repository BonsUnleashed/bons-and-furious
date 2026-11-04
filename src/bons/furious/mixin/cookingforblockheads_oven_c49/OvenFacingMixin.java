package bons.furious.mixin.cookingforblockheads_oven_c49;

import bons.furious.patch.cookingforblockheads_oven_c49.OvenFacing;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.blay09.mods.cookingforblockheads.tile.OvenBlockEntity;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * cookingforblockheads_oven_facing_from_state (Cooking for Blockheads 16.0.15, both sides, seen only by the client's
 * OvenRenderer; kind change, gameplay fix, off by default; no Cooking for Blockheads code carried). OvenBlockEntity.getFacing
 * answers the oven's block state facing instead of a field only the plain oven's first server tick fills (NORTH on every
 * client). See OvenFacing.
 */
@Mixin(value = OvenBlockEntity.class, remap = false)
public abstract class OvenFacingMixin {
    @ModifyReturnValue(method = "getFacing()Lnet/minecraft/core/Direction;", at = @At("RETURN"), require = 1, allow = 2)
    private Direction bons$cookingforblockheadsOvenFacing(Direction original) {
        return OvenFacing.facing(((OvenBlockEntity) (Object) this).m_58900_(), original);
    }
}
