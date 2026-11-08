package bons.furious.mixin.amendments_sway;

import bons.furious.patch.amendments_sway.AmendmentsSway;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * amendments_idle_sway_packet_skip (Amendments 1.21-2.2.7, Supplementaries Team License: our logic only; server side; since
 * 1.0.49). Trade-off (off by default). MixinExtras @WrapOperation of the one static
 * NetworkHelper.sendToAllClientPlayersTrackingEntity(Entity, CustomPacketPayload) call in WallLanternBlock.entityInside
 * (server branch; Moonlight 1.21.1's replacement of the Forge ChannelHandler call): the message is sent only when the entity
 * moved this tick (AmendmentsSway). The message object is still built (as before); the game event and the client branch are
 * unchanged. require = 0: when another mod already replaces this call the switch has nothing to change.
 */
@Mixin(targets = "net.mehvahdjukaar.amendments.common.block.WallLanternBlock", remap = false)
public abstract class WallLanternSwayMixin {
    @WrapOperation(method = "entityInside(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/Entity;)V",
            require = 0, allow = 1, at = @At(value = "INVOKE",
            target = "Lnet/mehvahdjukaar/moonlight/api/platform/network/NetworkHelper;sendToAllClientPlayersTrackingEntity(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload;)V"))
    private void bons$swayOnlyWhenMoved(Entity entity, CustomPacketPayload message, Operation<Void> original) {
        AmendmentsSway.send(entity, message, original);
    }
}
