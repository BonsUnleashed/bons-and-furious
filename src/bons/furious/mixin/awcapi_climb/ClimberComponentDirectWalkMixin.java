package bons.furious.mixin.awcapi_climb;

import bons.furious.patch.awcapi_climb.ClimberCollisions;
import com.llamalad7.mixinextras.injector.ModifyReceiver;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.nyfaria.awcapi.entity.ClimberComponent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.CollisionGetter;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * awcapi_collision_direct (Advanced Wall Climber API 1.0.3, all rights reserved: no awcapi code is carried; nested in Nyf's
 * Spiders 3.0.2; both sides; since 1.0.46).
 *
 * ClimberComponent.forEachCollisonBox(box, action) builds awcapi's filtered collision getter, takes its
 * getBlockCollisions(mob, box) iterable and runs {@code shapes.forEach(shape -> shape.forAllBoxes(action))}. Three
 * injections into that method, our own logic only, each returning what it was given:
 *  - @ModifyVariable at HEAD keeps {@code action};
 *  - @ModifyReceiver on the getBlockCollisions call keeps awcapi's own filtered getter and the box;
 *  - @WrapOperation on the final Iterable.forEach: when ClimberCollisions.applies (a plain box of at most 32 blocks), the
 *    walk hands the same boxes to {@code action} directly in the same order (see ClimberCollisions for why it is exact);
 *    otherwise the forEach runs as shipped. The iterable itself is lazy (built by the getter, never iterated on the fast
 *    path), so nothing awcapi or Minecraft computes is lost.
 * The kept references live in three fields of the component (one component per climber, used by that climber's thread only;
 * forEachCollisonBox does not re-enter itself) and are cleared by the wrapper. Allocation-free on the fast path apart from
 * the walk's own MutableBlockPos and CollisionContext (BlockCollisions makes the same two).
 */
@Mixin(value = ClimberComponent.class, remap = false)
public abstract class ClimberComponentDirectWalkMixin {
    @Shadow(remap = false)
    @Final
    private Mob mob;
    @Unique
    private CollisionGetter bons$climbReader;
    @Unique
    private AABB bons$climbBox;
    @Unique
    private Shapes.DoubleLineConsumer bons$climbAction;

    @ModifyVariable(method = "forEachCollisonBox", at = @At("HEAD"), argsOnly = true, remap = false)
    private Shapes.DoubleLineConsumer bons$keepClimbAction(Shapes.DoubleLineConsumer action) {
        this.bons$climbAction = action;
        return action;
    }

    @ModifyReceiver(method = "forEachCollisonBox", remap = false, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/CollisionGetter;m_186434_(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;)Ljava/lang/Iterable;"))
    private CollisionGetter bons$keepClimbReader(CollisionGetter reader, Entity entity, AABB box) {
        this.bons$climbReader = reader;
        this.bons$climbBox = box;
        return reader;
    }

    @WrapOperation(method = "forEachCollisonBox", remap = false, at = @At(value = "INVOKE",
            target = "Ljava/lang/Iterable;forEach(Ljava/util/function/Consumer;)V"))
    @SuppressWarnings({"rawtypes", "unchecked"})
    private void bons$directWalk(Iterable shapes, Consumer consumer, Operation<Void> original) {
        CollisionGetter reader = this.bons$climbReader;
        AABB box = this.bons$climbBox;
        Shapes.DoubleLineConsumer action = this.bons$climbAction;
        this.bons$climbReader = null;
        this.bons$climbBox = null;
        this.bons$climbAction = null;
        if (action == null || !ClimberCollisions.applies(this.mob, reader, box)) {
            original.call(shapes, consumer);
            return;
        }
        ClimberCollisions.announce();
        if (ClimberCollisions.SHADOW) {
            List<double[]> ours = ClimberCollisions.record(this.mob, reader, box);
            List<double[]> theirs = new ArrayList<>();
            Shapes.DoubleLineConsumer recording = (x0, y0, z0, x1, y1, z1) -> {
                theirs.add(new double[]{x0, y0, z0, x1, y1, z1});
                action.m_83161_(x0, y0, z0, x1, y1, z1);
            };
            original.call(shapes, (Consumer<VoxelShape>) shape -> shape.m_83286_(recording));
            ClimberCollisions.compare(theirs, ours, box);
            return;
        }
        ClimberCollisions.walk(this.mob, reader, box, action);
    }
}
