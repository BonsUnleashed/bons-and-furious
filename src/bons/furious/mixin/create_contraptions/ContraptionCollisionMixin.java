package bons.furious.mixin.create_contraptions;

import bons.furious.patch.create_contraptions.CollisionUnion;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.simibubi.create.content.contraptions.Contraption;
import java.util.List;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * create_collision_single_pass (Create 6.0.8, MIT, both sides; tested build 6.0.8).
 *
 * In the collider lambda of Contraption.gatherBBsOffThread (lambda$gatherBBsOffThread$24), Create's loop and its
 * collision-shape calls stay as they are; the Shapes.joinUnoptimized call collects the moved shape instead of joining it,
 * optimize() computes the box list in one pass (CollisionUnion) and toAabbs() on the same object hands that list over,
 * which is the list Create's join chain, optimize() and toAabbs() produce. A shape set outside CollisionUnion's
 * preconditions is joined by Create's own chain at optimize(). This is not Create 6.0.10's later collision change (with
 * its NullPointerException reports), only the same result built faster.
 */
@Mixin(value = Contraption.class, remap = false)
public abstract class ContraptionCollisionMixin {
    @WrapOperation(method = "lambda$gatherBBsOffThread$24()Ljava/util/List;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/shapes/Shapes;m_83148_(Lnet/minecraft/world/phys/shapes/VoxelShape;Lnet/minecraft/world/phys/shapes/VoxelShape;Lnet/minecraft/world/phys/shapes/BooleanOp;)Lnet/minecraft/world/phys/shapes/VoxelShape;"))
    private VoxelShape bons$collect(VoxelShape combined, VoxelShape moved, BooleanOp op, Operation<VoxelShape> original) {
        return CollisionUnion.join(combined, moved, op, original);
    }

    @WrapOperation(method = "lambda$gatherBBsOffThread$24()Ljava/util/List;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/shapes/VoxelShape;m_83296_()Lnet/minecraft/world/phys/shapes/VoxelShape;"))
    private VoxelShape bons$combined(VoxelShape combined, Operation<VoxelShape> original) {
        return CollisionUnion.optimize(combined, original);
    }

    @WrapOperation(method = "lambda$gatherBBsOffThread$24()Ljava/util/List;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/shapes/VoxelShape;m_83299_()Ljava/util/List;"))
    private List<AABB> bons$boxes(VoxelShape shape, Operation<List<AABB>> original) {
        return CollisionUnion.toAabbs(shape, original);
    }
}
