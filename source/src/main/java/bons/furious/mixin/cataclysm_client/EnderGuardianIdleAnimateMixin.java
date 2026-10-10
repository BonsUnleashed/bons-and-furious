package bons.furious.mixin.cataclysm_client;

import bons.furious.patch.cataclysm_client.IdleAnimateSkip;
import com.github.L_Ender.cataclysm.client.model.entity.Ender_Guardian_Model;
import com.github.L_Ender.lionfishapi.server.animation.IAnimatedEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * cataclysm_idle_animate_skip (Ender_Guardian_Model, L_Ender's Cataclysm, CC-BY-NC-ND-4.0: no Cataclysm code is carried;
 * 1.21.1 tested build L_Ender's Cataclysm 1.21.1-3.33; client).
 *
 * @Inject right after the animator.update(entity) call in Ender_Guardian_Model.animate, cancellable: when IdleAnimateSkip.idle says
 * the entity plays no animation, the rest of the method (keyframe calls that are all no-ops then; see IdleAnimateSkip for
 * the bytecode proof) is skipped; otherwise it runs as shipped. The method is over HotSpot's huge-method limit and runs
 * interpreted, so the callback's CallbackInfo (one per frame per model) is the only cost added.
 *
 * Ported to 1.21.1: Ender_Guardian_Model.animate in 3.33 is the 3.16 method instruction for instruction; nothing changed.
 */
@Mixin(value = Ender_Guardian_Model.class, remap = false)
public abstract class EnderGuardianIdleAnimateMixin {
    @Inject(method = "animate(Lcom/github/L_Ender/lionfishapi/server/animation/IAnimatedEntity;FFFFF)V", cancellable = true, at = @At(value = "INVOKE", shift = At.Shift.AFTER,
            target = "Lcom/github/L_Ender/lionfishapi/client/model/Animations/ModelAnimator;update(Lcom/github/L_Ender/lionfishapi/server/animation/IAnimatedEntity;)V"))
    private void bons$skipIdleKeyframes(IAnimatedEntity entity, float f, float f1, float f2, float f3, float f4, CallbackInfo ci) {
        if (IdleAnimateSkip.idle(entity)) ci.cancel();
    }
}
