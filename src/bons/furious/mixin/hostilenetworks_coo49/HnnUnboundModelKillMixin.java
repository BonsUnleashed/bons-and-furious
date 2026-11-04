package bons.furious.mixin.hostilenetworks_coo49;

import bons.furious.patch.hostilenetworks_coo49.HnnKillFix;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * hostilenetworks_unbound_model_kill_fix (Hostile Neural Networks 5.3.3 HostileEvents.updateModels; server). Only when the
 * deep learner holds a data model whose id is not registered, the corrected loop of HnnKillFix runs and the method returns
 * (the original throws NullPointerException out of the kill event there); every other learner runs the original.
 */
@Mixin(targets = "dev.shadowsoffire.hostilenetworks.HostileEvents", remap = false)
public abstract class HnnUnboundModelKillMixin {
    @Inject(method = "updateModels(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/EntityType;I)V",
            at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    private static void bons$hostilenetworksCoo49UpdateModels(ItemStack learner, EntityType<?> type, int bonus, CallbackInfo ci) {
        if (HnnKillFix.handled(learner, type, bonus)) ci.cancel();
    }
}
