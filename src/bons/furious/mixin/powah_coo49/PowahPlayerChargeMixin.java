package bons.furious.mixin.powah_coo49;

import bons.furious.patch.powah_coo49.PowahPlayerCharge;
import java.util.function.Predicate;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import owmii.powah.forge.ForgeEnvHandler;

/**
 * powah_idle_player_charge_skip (Powah 5.0.11, server; kind change, off by default; no Powah code carried):
 * ForgeEnvHandler.chargeItemsInPlayerInv answers 0 at once when it can only offer 0 (maxPerSlot <= 0 or maxTotal <= 0), so
 * the inventory copies, the ChargeableItemsEvent post and the zero offers do not happen. See PowahPlayerCharge.
 */
@Mixin(value = ForgeEnvHandler.class, remap = false)
public abstract class PowahPlayerChargeMixin {
    @Inject(method = "chargeItemsInPlayerInv(Lnet/minecraft/world/entity/player/Player;JJLjava/util/function/Predicate;)J", at = @At("HEAD"),
            cancellable = true, require = 1, allow = 1)
    private void bons$powahIdlePlayerChargeSkip(Player player, long maxPerSlot, long maxTotal, Predicate<?> allowStack, CallbackInfoReturnable<Long> cir) {
        if (PowahPlayerCharge.idle(maxPerSlot, maxTotal)) cir.setReturnValue(0L);
    }
}
