package bons.furious.mixin.valkyrien_block_registration;

import bons.furious.patch.valkyrien_block_registration.LightRegistration;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.mod.common.config.MassDatapackResolver;

/**
 * valkyrien_block_registration_lazy_cache (Valkyrien Skies 2.4.11, Apache-2.0; both sides).
 *
 * Two wrappers in MassDatapackResolver.registerAllBlockStates, no Valkyrien Skies code: its one BlockState.isSolid()
 * call and its one two-argument BlockState.getCollisionShape(getter, pos) call. For a state whose block-state cache
 * ModernFix has not built yet they answer what the cache would hold without building it (LightRegistration); every
 * other state runs the original call.
 */
@Mixin(value = MassDatapackResolver.class, remap = false)
public abstract class RegisterAllBlockStatesMixin {
    @WrapOperation(method = "registerAllBlockStates", require = 1, allow = 1, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/state/BlockState;m_280296_()Z"))
    private boolean bons$isSolid(BlockState state, Operation<Boolean> original) {
        return LightRegistration.isSolid(state, original);
    }

    @WrapOperation(method = "registerAllBlockStates", require = 1, allow = 1, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/state/BlockState;m_60812_(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/phys/shapes/VoxelShape;"))
    private VoxelShape bons$collisionShape(BlockState state, BlockGetter getter, BlockPos pos, Operation<VoxelShape> original) {
        return LightRegistration.collisionShape(state, getter, pos, original);
    }
}
