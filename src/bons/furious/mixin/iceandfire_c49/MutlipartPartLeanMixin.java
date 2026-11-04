package bons.furious.mixin.iceandfire_c49;

import bons.furious.patch.iceandfire_c49.MultipartLean;
import com.github.alexthe666.iceandfire.entity.EntityMutlipartPart;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.Mixin;

/**
 * iceandfire_multipart_part_lean (Ice and Fire 2.1.13-1.20.1-beta-5; the method runs on the server only; since 1.0.49).
 * collideWithNearbyEntities is wrapped: with the switch on, bons.furious.patch.iceandfire_c49.MultipartLean.collide makes
 * the same calls in the same order as the stock stream (see there); with the runtime flag off the original runs.
 */
@Mixin(value = EntityMutlipartPart.class, remap = false)
public abstract class MutlipartPartLeanMixin {
    @WrapMethod(method = "collideWithNearbyEntities()V", remap = false)
    private void bons$leanCollide(Operation<Void> original) {
        if (!MultipartLean.enabled) {
            original.call();
            return;
        }
        MultipartLean.collide((EntityMutlipartPart) (Object) this);
    }
}
