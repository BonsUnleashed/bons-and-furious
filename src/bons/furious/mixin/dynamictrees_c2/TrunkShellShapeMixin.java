package bons.furious.mixin.dynamictrees_c2;

import bons.furious.patch.dynamictrees_c2.ThickShapes;
import com.ferreusveritas.dynamictrees.block.branch.TrunkShellBlock;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * dynamictrees_thick_shape_memo (Dynamic Trees 1.20.1-1.4.11, both sides): TrunkShellBlock.getShape's lambda
 * (lambda$getShape$5: Shapes.create(muse core shape bounds moved by the offset to the core)), queried for the eight shell
 * blocks around every thick trunk block. The create call is answered from ThickShapes' table when the box is exactly a
 * thick trunk box moved by an offset in {-1, 0, 1}^3; any other box (a thin core, a foreign shape) is created as before.
 * The muse lookup, its tick scheduling and the core's own getShape call run unchanged. MIT target, no Dynamic Trees code.
 */
@Mixin(value = TrunkShellBlock.class, remap = false)
public abstract class TrunkShellShapeMixin {
    @WrapOperation(method = "lambda$getShape$5", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/phys/shapes/Shapes;m_83064_(Lnet/minecraft/world/phys/AABB;)Lnet/minecraft/world/phys/shapes/VoxelShape;"))
    private static VoxelShape bons$shellShape(AABB box, Operation<VoxelShape> original) {
        VoxelShape s = ThickShapes.enabled ? ThickShapes.memo(box) : null;
        return s != null ? s : original.call(box);
    }
}
