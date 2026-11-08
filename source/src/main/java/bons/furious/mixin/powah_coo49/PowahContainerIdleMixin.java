package bons.furious.mixin.powah_coo49;

import bons.furious.patch.powah_coo49.PowahContainerCharge;
import bons.furious.patch.powah_coo49.PowahContainerIdle;
import net.minecraft.world.Container;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import owmii.powah.util.ChargeUtil;

/**
 * powah_container_charge_idle_skip (Powah 6.2.10, server; kind change, off by default; no Powah code carried):
 * ChargeUtil.chargeItemsInContainer answers 0 without touching the container when the hopper has no energy or no charge
 * rate, and otherwise walks it as Powah does (PowahContainerCharge.charge) but marks the container changed only when
 * something was charged. The marker method tells the streamless switch's mixin that this one is in the game. See
 * PowahContainerIdle.
 */
@Mixin(value = ChargeUtil.class, remap = false)
public abstract class PowahContainerIdleMixin {
    @Inject(method = "chargeItemsInContainer(Lnet/minecraft/world/Container;JJ)J", at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    private static void bons$powahContainerChargeIdleSkip(Container container, long maxPerSlot, long maxTotal, CallbackInfoReturnable<Long> cir) {
        if (!PowahContainerIdle.enabled) return;
        if (maxPerSlot <= 0L || maxTotal <= 0L) {
            cir.setReturnValue(0L);
            return;
        }
        cir.setReturnValue(PowahContainerCharge.charge(container, maxPerSlot, maxTotal, true));
    }

    /** Marker (never called): its presence in ChargeUtil means this mixin applied. */
    @Unique
    private static boolean bons$powahContainerIdleMarker() {
        return true;
    }
}
