package bons.furious.mixin.shouldersurfing_c49;

import bons.furious.patch.shouldersurfing_c49.CameraSweep;
import com.github.exopandora.shouldersurfing.api.client.world.phys.BlockCollisionPredicate;
import com.github.exopandora.shouldersurfing.api.client.world.phys.IObjectPicker;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * shouldersurfing_camera_sweep_prefilter (Shoulder Surfing Reloaded 5.2.0, tested; MIT; client side): at the head of
 * DynamicOffsets.calcDynamicOffsets, CameraSweep.plan scans the blocks around the swept region once and returns the
 * method's own no-hit result when none can be hit; otherwise the loop's ObjectPicker.clip calls go through CameraSweep.clip,
 * which answers "miss" for a step whose segment box holds no block that can be hit. The predicate handed to the plan is
 * the method's own (getBlockCollisionPredicate, the same object the loop passes to clip).
 */
@Mixin(targets = "com.github.exopandora.shouldersurfing.client.event.handler.ComputeTargetCameraOffsetEventHandlerImpl$DynamicOffsets", remap = false)
public abstract class DynamicOffsetsPrescanMixin {
    @Shadow
    private static BlockCollisionPredicate getBlockCollisionPredicate() {
        throw new AssertionError();
    }

    @Inject(method = "calcDynamicOffsets(Lnet/minecraft/client/Camera;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;",
            at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    private static void bons$shouldersurfingC49Prescan(Camera camera, Entity cameraEntity, BlockGetter level, Vec3 targetOffset, CallbackInfoReturnable<Vec3> cir) {
        Vec3 answer = CameraSweep.plan(camera, cameraEntity, level, targetOffset, getBlockCollisionPredicate());
        if (answer != null) cir.setReturnValue(answer);
    }

    @Redirect(method = "calcDynamicOffsets(Lnet/minecraft/client/Camera;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;",
            at = @At(value = "INVOKE", target = "Lcom/github/exopandora/shouldersurfing/api/client/world/phys/IObjectPicker;clip(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/world/level/ClipContext;Lcom/github/exopandora/shouldersurfing/api/client/world/phys/BlockCollisionPredicate;)Lnet/minecraft/world/phys/BlockHitResult;"),
            require = 1, allow = 1)
    private static BlockHitResult bons$shouldersurfingC49Clip(IObjectPicker picker, BlockGetter level, ClipContext context, BlockCollisionPredicate predicate) {
        return CameraSweep.clip(picker, level, context, predicate);
    }
}
