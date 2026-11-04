package bons.furious.mixin.powah_coo49;

import bons.furious.patch.powah_coo49.PowahContainerCharge;
import bons.furious.patch.powah_coo49.PowahContainerIdle;
import net.minecraft.world.Container;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import owmii.powah.forge.ForgeEnvHandler;

/**
 * powah_container_charge_streamless (Powah 5.0.11, server; kind opt, on by default; no Powah code carried):
 * ForgeEnvHandler.chargeItemsInContainer answers from PowahContainerCharge.charge, the same getItem calls, offers,
 * setChanged and total as Powah's stream-built slot list, without the range / stream / lambda / list objects. Steps aside
 * while powah_container_charge_idle_skip's mixin is in the game and on (that one then answers the call). See
 * PowahContainerCharge.
 */
@Mixin(value = ForgeEnvHandler.class, remap = false)
public abstract class PowahContainerChargeMixin {
    @Inject(method = "chargeItemsInContainer(Lnet/minecraft/world/Container;JJ)J", at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    private void bons$powahContainerChargeStreamless(Container container, long maxPerSlot, long maxTotal, CallbackInfoReturnable<Long> cir) {
        if (!PowahContainerCharge.enabled || PowahContainerIdle.active(ForgeEnvHandler.class)) return;
        cir.setReturnValue(PowahContainerCharge.charge(container, maxPerSlot, maxTotal, false));
    }
}
