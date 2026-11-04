package bons.furious.mixin.uniqueaccessories_coo49;

import bons.furious.patch.uniqueaccessories_coo49.UaRoseCandidates;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * uniqueaccessories_rose_cleanup_candidates (Unique Accessories 1.3.3, All Rights Reserved; server side), on
 * RoseOfTemptationEvents: @Inject at RETURN of applyUniformSpeedWithTTL notes the mob in its level's candidate set; a
 * cancellable @Inject at HEAD of cleanupAroundPlayer returns when the level holds no candidate (see UaRoseCandidates).
 * Our own logic only; nothing of the mod is carried.
 */
@Mixin(targets = "net.genzyuro.uniqueaccessories.system.RoseOfTemptationEvents", remap = false)
public abstract class UaRoseCleanupMixin {
    @Inject(method = "applyUniformSpeedWithTTL(Lnet/minecraft/world/entity/Mob;J)V", at = @At("RETURN"), require = 1, remap = false)
    private static void bons$uniqueaccessoriesCoo49RoseBoosted(Mob mob, long now, CallbackInfo ci) {
        UaRoseCandidates.boosted(mob);
    }

    @Inject(method = "cleanupAroundPlayer(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/player/Player;D)V",
            at = @At("HEAD"), cancellable = true, require = 1, allow = 1, remap = false)
    private static void bons$uniqueaccessoriesCoo49RoseCleanup(ServerLevel level, Player player, double radius, CallbackInfo ci) {
        if (UaRoseCandidates.skipCleanup(level, player)) ci.cancel();
    }
}
