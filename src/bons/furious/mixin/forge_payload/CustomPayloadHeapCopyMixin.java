package bons.furious.mixin.forge_payload;

import bons.furious.patch.forge_payload.PayloadCopies;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * forge_custom_payload_heap_copy (Forge 47.4.16). Fix.
 *
 * getData() copies the packet's data with ByteBuf.copy(), which for a packet read from the network is a pooled direct
 * buffer that Forge's mod-channel event never releases. The one copy() call now makes the same copy as an unpooled heap
 * buffer (see PayloadCopies): same bytes, indices, capacity and maximum capacity, reclaimed by the garbage collector.
 */
@Mixin(value = ClientboundCustomPayloadPacket.class, remap = false)
public abstract class CustomPayloadHeapCopyMixin {
    @Redirect(method = "m_132045_", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/FriendlyByteBuf;copy()Lio/netty/buffer/ByteBuf;"))
    private ByteBuf bons$heapCopy(FriendlyByteBuf data) {
        return PayloadCopies.heapCopy(data);
    }
}
