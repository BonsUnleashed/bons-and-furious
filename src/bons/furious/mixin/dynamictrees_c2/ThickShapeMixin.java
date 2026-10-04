package bons.furious.mixin.dynamictrees_c2;

import bons.furious.patch.dynamictrees_c2.ThickShapes;
import com.ferreusveritas.dynamictrees.block.branch.ThickBranchBlock;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * dynamictrees_thick_shape_memo (Dynamic Trees 1.20.1-1.4.11, both sides): ThickBranchBlock.getShape (m_5940_) for radius
 * 9..24 builds Shapes.create(new AABB(...)) on every query. The create call is answered from ThickShapes' table when the box
 * is exactly a thick trunk box (same geometry, one shared immutable shape per radius); any other box is created as before.
 * The radius 1..8 path (BasicBranchBlock's connection shapes) is untouched. MIT target, no Dynamic Trees code carried.
 */
@Mixin(value = ThickBranchBlock.class, remap = false)
public abstract class ThickShapeMixin {
    @WrapOperation(method = "m_5940_", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/phys/shapes/Shapes;m_83064_(Lnet/minecraft/world/phys/AABB;)Lnet/minecraft/world/phys/shapes/VoxelShape;"))
    private VoxelShape bons$thickShape(AABB box, Operation<VoxelShape> original) {
        VoxelShape s = ThickShapes.enabled ? ThickShapes.memo(box) : null;
        return s != null ? s : original.call(box);
    }
}
