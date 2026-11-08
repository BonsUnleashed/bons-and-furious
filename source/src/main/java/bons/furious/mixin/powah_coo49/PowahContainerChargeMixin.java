package bons.furious.mixin.powah_coo49;

import bons.furious.patch.powah_coo49.PowahContainerCharge;
import bons.furious.patch.powah_coo49.PowahContainerIdle;
import net.minecraft.world.Container;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import owmii.powah.util.ChargeUtil;

/**
 * powah_container_charge_streamless (Powah 6.2.10, server; kind opt, on by default; no Powah code carried):
 * ChargeUtil.chargeItemsInContainer answers from PowahContainerCharge.charge, the same getItem calls, capability lookups,
 * offers, setChanged and total as Powah's stream-built slot list, without the range / stream / lambda / list objects. Steps
 * aside while powah_container_charge_idle_skip's mixin is in the game and on (that one then answers the call). See
 * PowahContainerCharge.
 */
@Mixin(value = ChargeUtil.class, remap = false)
public abstract class PowahContainerChargeMixin {
    @Inject(method = "chargeItemsInContainer(Lnet/minecraft/world/Container;JJ)J", at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    private static void bons$powahContainerChargeStreamless(Container container, long maxPerSlot, long maxTotal, CallbackInfoReturnable<Long> cir) {
        if (!PowahContainerCharge.enabled || PowahContainerIdle.active(ChargeUtil.class)) return;
        cir.setReturnValue(PowahContainerCharge.charge(container, maxPerSlot, maxTotal, false));
    }
}
