package bons.furious.mixin.moreartifacts_coo49;

import bons.furious.patch.moreartifacts_coo49.MaTickEnd;
import net.minecraftforge.event.TickEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * moreartifacts_equip_tick_end_only (More Artifacts 1.5.5, All Rights Reserved; both sides; trade-off, off by default): a
 * cancellable @Inject at HEAD of DamageEvents.onTick returns for the START phase of the player tick, so the equip check
 * and the damage totals it keeps run once per tick, at the END phase (see MaTickEnd). Our own logic only.
 */
@Mixin(targets = "net.gobies.moreartifacts.event.DamageEvents", remap = false)
public abstract class MaTickEndMixin {
    @Inject(method = "onTick(Lnet/minecraftforge/event/TickEvent$PlayerTickEvent;)V", at = @At("HEAD"), cancellable = true,
            require = 1, allow = 1, remap = false)
    private static void bons$moreartifactsCoo49TickEnd(TickEvent.PlayerTickEvent event, CallbackInfo ci) {
        if (MaTickEnd.skip(event)) ci.cancel();
    }
}
