package bons.furious.mixin.mowziesmobs_attach_c49;

import bons.furious.patch.mowziesmobs_attach_c49.MowzieAttachDedupe;
import com.bobmowzie.mowziesmobs.server.capability.CapabilityHandler;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * mowziesmobs_capability_attach_dedupe (Mowzie's Mobs 1.7.3 and 1.8.2, both sides; exact variant): Mowzie's second
 * capability listener, CapabilityHandler.attachEntityCapability, returns at once when the event already holds Mowzie's own
 * first set of providers for every capability it would add (MowzieAttachDedupe). Our own logic only.
 */
@Mixin(value = CapabilityHandler.class, remap = false)
public abstract class MowzieAttachDedupeMixin {
    @Inject(method = "attachEntityCapability(Lnet/minecraftforge/event/AttachCapabilitiesEvent;)V", at = @At("HEAD"), cancellable = true)
    private static void bons$skipSecondCopy(AttachCapabilitiesEvent<Entity> event, CallbackInfo ci) {
        if (MowzieAttachDedupe.alreadyAttached(event)) ci.cancel();
    }
}
