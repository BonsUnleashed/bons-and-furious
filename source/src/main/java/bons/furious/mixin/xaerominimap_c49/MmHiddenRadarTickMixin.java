package bons.furious.mixin.xaerominimap_c49;

import bons.furious.patch.xaerominimap_c49.RadarPacing;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import xaero.common.minimap.MinimapProcessor;
import xaero.hud.minimap.radar.RadarSession;

/**
 * xaerominimap_hidden_radar_skip (Xaero's Minimap 26.6.0 for NeoForge 1.21.1, client, since 1.0.49; change, off by default).
 * MixinExtras @WrapOperation on MinimapProcessor.onClientTick's radarSession.update call: left out while the minimap is
 * hidden and no screen is open (RadarPacing). No Minimap code is carried (All Rights Reserved).
 */
@Mixin(value = MinimapProcessor.class, remap = false)
public abstract class MmHiddenRadarTickMixin {
    @WrapOperation(method = "onClientTick()V", require = 1, allow = 1, at = @At(value = "INVOKE",
            target = "Lxaero/hud/minimap/radar/RadarSession;update(Lnet/minecraft/client/multiplayer/ClientLevel;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/player/Player;)V"))
    private void bons$xaerominimapC49HiddenTick(RadarSession session, ClientLevel world, Entity renderEntity, Player player, Operation<Void> original) {
        RadarPacing.tick(0, session, world, renderEntity, player, original);
    }
}
