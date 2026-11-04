package bons.furious.mixin.brutality_coo49;

import bons.furious.patch.brutality_coo49.BrutalityBlight;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * brutality_blight_scan_idle_skip (Brutality 0.8.5, MIT; client; since 1.0.49): at the head of
 * ForgeClientParticleHandler.spawnMiracleBlightParticles, while no Brutality effect capability is blighted, the profiler's
 * "getEntities" counter is counted as the search would count it and the method returns (the search could act on nothing:
 * particles and random draws happen only for blighted capabilities). Otherwise it runs as shipped. Helper BrutalityBlight.
 */
@Mixin(targets = "net.goo.brutality.event.forge.client.ForgeClientParticleHandler", remap = false)
public abstract class BrutalityBlightScanMixin {
    @Inject(method = "spawnMiracleBlightParticles(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/level/Level;)V",
            at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    private static void bons$brutalityCoo49BlightSkip(Player player, Level level, CallbackInfo ci) {
        if (BrutalityBlight.skip(player, level)) ci.cancel();
    }
}
