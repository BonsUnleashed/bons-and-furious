package bons.furious.mixin.refinedmod_coo49;

import bons.furious.patch.refinedmod_coo49.RefinedFixes;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * refinedmod_wave_terrain_server_only (Refined Mod 1.1.6, All Rights Reserved; client; since 1.0.49; fix):
 * CustomWaveEntity.extinguishNearbyFires and convertLavaToCobblestone return at their head on a client level, so the client
 * no longer writes made-up air / cobblestone into its own world; the server's sweeps reach it as block updates. Helper
 * RefinedFixes.
 */
@Mixin(targets = "net.refinedrain.refinedmod.entity.spells.CustomWaveEntity", remap = false)
public abstract class RefinedWaveClientTerrainMixin {
    @Inject(method = {"extinguishNearbyFires()V", "convertLavaToCobblestone()V"}, at = @At("HEAD"), cancellable = true, require = 2, allow = 2)
    private void bons$refinedmodCoo49ClientTerrain(CallbackInfo ci) {
        if (RefinedFixes.skipSweep(((Entity) (Object) this).m_9236_(), true)) ci.cancel();
    }
}
