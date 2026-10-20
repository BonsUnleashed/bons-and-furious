package bons.furious.mixin.punchy;

import bons.furious.patch.punchy.EmfTraceSkip;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import punchy.client.debug.EmfEtfFreshAnimationsDebug;

/**
 * punchy_emf_trace_skip (Punchy 2.8g, client): Punchy's per-entity debug trace push / pop return at once (EmfTraceSkip
 * explains why nothing observable changes). Our own logic only. The handlers are a few bytes so HotSpot inlines them and
 * drops the CallbackInfo. The record methods get a HEAD hook that only counts calls in shadow mode.
 */
@Mixin(value = EmfEtfFreshAnimationsDebug.class, remap = false)
public abstract class EmfDebugTraceSkipMixin {
    @Inject(method = "pushLivingEntityRender(Lnet/minecraft/world/entity/LivingEntity;Ljava/lang/Object;Ljava/lang/Object;)V", at = @At("HEAD"), cancellable = true)
    private static void bons$punchyPush(LivingEntity entity, Object renderer, Object model, CallbackInfo ci) {
        if (EmfTraceSkip.skip()) ci.cancel();
    }

    @Inject(method = "popLivingEntityRender()V", at = @At("HEAD"), cancellable = true)
    private static void bons$punchyPop(CallbackInfo ci) {
        if (EmfTraceSkip.skip()) ci.cancel();
    }

    @Inject(method = "recordResetPose", at = @At("HEAD"))
    private static void bons$punchyRecordPose(CallbackInfo ci) {
        EmfTraceSkip.recorded();
    }

    @Inject(method = "recordExplicitTransformApplication", at = @At("HEAD"))
    private static void bons$punchyRecordTransform(CallbackInfo ci) {
        EmfTraceSkip.recorded();
    }
}
