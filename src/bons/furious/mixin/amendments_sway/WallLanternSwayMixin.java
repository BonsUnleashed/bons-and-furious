package bons.furious.mixin.amendments_sway;

import bons.furious.patch.amendments_sway.AmendmentsSway;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.mehvahdjukaar.amendments.common.block.WallLanternBlock;
import net.mehvahdjukaar.moonlight.api.platform.network.ChannelHandler;
import net.mehvahdjukaar.moonlight.api.platform.network.Message;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * amendments_idle_sway_packet_skip (Amendments 2.2.3, Supplementaries Team License: our logic only; server side; since
 * 1.0.49). Trade-off (off by default). MixinExtras @WrapOperation of the one ChannelHandler.sentToAllClientPlayersTrackingEntity
 * call in WallLanternBlock.entityInside (m_7892_, server branch): the message is sent only when the entity moved this tick
 * (AmendmentsSway). The message object is still built (as before); the game event and the client branch are unchanged.
 * require = 0: when another mod already replaces this call the switch has nothing to change.
 */
@Mixin(value = WallLanternBlock.class, remap = false)
public abstract class WallLanternSwayMixin {
    @WrapOperation(method = "m_7892_", require = 0, allow = 1, at = @At(value = "INVOKE",
            target = "Lnet/mehvahdjukaar/moonlight/api/platform/network/ChannelHandler;sentToAllClientPlayersTrackingEntity(Lnet/minecraft/world/entity/Entity;Lnet/mehvahdjukaar/moonlight/api/platform/network/Message;)V"))
    private void bons$swayOnlyWhenMoved(ChannelHandler channel, Entity entity, Message message, Operation<Void> original) {
        AmendmentsSway.send(channel, entity, message, original);
    }
}
