package bons.furious.mixin.goetycataclysm_coo49;

import bons.furious.patch.goetycataclysm_coo49.GcGolems;
import net.minecraftforge.event.level.LevelEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * goety_cataclysm_golem_unload_skip (Goety Cataclysm 1.20-1.9.2, all rights reserved: no code carried; both sides; exact).
 * GCEvents.worldUnload only builds array copies it discards (GolemType.values() clone, an ArrayUtils.remove copy per
 * constant of NEW_GOLEM_TYPES); the handler returns at its start instead (GcGolems.skipSweep).
 */
@Mixin(targets = "com.Polarice3.goety_cataclysm.common.events.GCEvents", remap = false)
public abstract class GcGolemUnloadSkipMixin {
    @Inject(method = "worldUnload(Lnet/minecraftforge/event/level/LevelEvent$Unload;)V", at = @At("HEAD"), cancellable = true)
    private static void bons$goetycataclysmSkipUnloadSweep(LevelEvent.Unload event, CallbackInfo ci) {
        if (GcGolems.skipSweep()) ci.cancel();
    }
}
