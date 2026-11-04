package bons.furious.mixin.alexsmobs_coo49;

import bons.furious.patch.alexsmobs_coo49.LevelMapRelease;
import com.github.alexthe666.alexsmobs.event.ServerEvents;
import java.util.Map;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * alexsmobs_release_level_maps (Alex's Mobs 1.22.9): at the end of ServerEvents' static initializer its
 * BEACHED_CACHALOT_WHALE_SPAWNER_MAP is handed to LevelMapRelease (removed from at LevelEvent.Unload). See LevelMapRelease.
 */
@Mixin(value = ServerEvents.class, remap = false)
public abstract class ServerEventsWhaleReleaseMixin {
    @Shadow
    @Final
    private static Map<?, ?> BEACHED_CACHALOT_WHALE_SPAWNER_MAP;

    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void bons$handOverWhaleSpawners(CallbackInfo ci) {
        LevelMapRelease.whaleSpawners(BEACHED_CACHALOT_WHALE_SPAWNER_MAP);
    }
}
