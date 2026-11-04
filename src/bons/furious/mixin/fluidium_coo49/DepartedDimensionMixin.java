package bons.furious.mixin.fluidium_coo49;

import bons.furious.patch.fluidium_coo49.DepartedRefresh;
import net.minecraftforge.event.entity.player.PlayerEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * fluidium_departed_dimension_refresh (Fluidium 1.20.1-1.4.0, server; kind change, gameplay fix, off by default; no Fluidium
 * code carried). At the return of ActiveChunks.dimChange the departed dimension is marked for a rebuild too (Fluidium marks
 * only the one the player arrived in). See DepartedRefresh.
 */
@Mixin(targets = "me.kall.fluidium.common.data.ActiveChunks", remap = false)
public abstract class DepartedDimensionMixin {
    @Inject(method = "dimChange(Lnet/minecraftforge/event/entity/player/PlayerEvent$PlayerChangedDimensionEvent;)V", at = @At("RETURN"), require = 1)
    private static void bons$fluidiumDeparted(PlayerEvent.PlayerChangedDimensionEvent event, CallbackInfo ci) {
        DepartedRefresh.departed(event);
    }
}
