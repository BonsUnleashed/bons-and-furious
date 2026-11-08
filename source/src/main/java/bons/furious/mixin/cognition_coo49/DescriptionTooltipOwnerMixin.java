package bons.furious.mixin.cognition_coo49;

import bons.furious.patch.cognition_coo49.TooltipOwner;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * cognition_tooltip_owner_first (Cognition, MIT; 1.21.1 tested build: Cognition 2.4.13 for NeoForge 1.21.1; client; exact;
 * no Cognition code carried). DescriptionTooltips.handleTooltip returns at once for items whose creator mod is not Cognition
 * (it would add nothing for them after building the description string and polling the shift keys). See TooltipOwner.
 *
 * Ported to 1.21.1: Cognition's mod id is "cognition" (experienceobelisk in 2.4.12 for 1.20.1), and handleTooltip compares
 * the creator mod id with that string; the method body is otherwise the same.
 */
@Mixin(targets = "com.cyanogen.cognition.event.DescriptionTooltips", remap = false)
public abstract class DescriptionTooltipOwnerMixin {
    @Inject(method = "handleTooltip(Lnet/neoforged/neoforge/event/entity/player/ItemTooltipEvent;)V", at = @At("HEAD"), cancellable = true, require = 1)
    private static void bons$cognitionTooltipOwner(ItemTooltipEvent event, CallbackInfo ci) {
        if (TooltipOwner.foreign(event)) ci.cancel();
    }
}
