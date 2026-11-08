package bons.furious.mixin.cucumber_coo49;

import bons.furious.patch.cucumber_coo49.CucumberTagTooltips;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * cucumber_tag_tooltip_presence_first (Cucumber 1.21.1-8.0.16 TagTooltipHandler.onItemTooltip, a NeoForge ItemTooltipEvent
 * listener at LOW priority; client). With CTRL not held the "Hold CTRL for tags" line is decided from tag presence
 * (CucumberTagTooltips); otherwise the original runs. No Cucumber code is carried.
 */
@Mixin(targets = "com.blakebr0.cucumber.client.handler.TagTooltipHandler", remap = false)
public abstract class CucumberTagTooltipMixin {
    @Inject(method = "onItemTooltip(Lnet/neoforged/neoforge/event/entity/player/ItemTooltipEvent;)V", at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    private void bons$cucumberCoo49Tooltip(ItemTooltipEvent event, CallbackInfo ci) {
        if (CucumberTagTooltips.handled(event)) ci.cancel();
    }
}
