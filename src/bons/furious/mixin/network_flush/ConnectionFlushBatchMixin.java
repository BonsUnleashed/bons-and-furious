package bons.furious.mixin.network_flush;

import bons.furious.patch.network_flush.FlushBatch;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.EventLoop;
import net.minecraft.network.Connection;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * vanilla_connection_flush_batching (Minecraft 1.20.1 on Forge 47.4.16, Netty 4.1.82, both sides; acts on the server
 * thread): while a FlushBatch is open on the calling thread, Connection.send (m_243124_) marks a packet without send
 * listener for its connection; sendPacket (m_129520_) then hands Minecraft's own task to the event loop with lazyExecute
 * (same queue, same order, no wakeup) wrapped in a BatchedWrite, and doSendPacket (m_243087_), run by that task, writes
 * the packet with Channel.write instead of writeAndFlush unless it switches the protocol. Everything else in these
 * methods, including other mods' hooks (ModernFix's smart_ingredient_sync wrapper and Bad Packets' listener on the write
 * future), runs unchanged. tick (m_129483_) opens a batch for this connection's own tick; its closing Channel.flush is the
 * batch's flush. Four fields; no Minecraft code is carried.
 */
@Mixin(value = Connection.class, remap = false)
public abstract class ConnectionFlushBatchMixin implements FlushBatch.Member {
    /** Event loop thread only. */
    @Unique
    private boolean bons$writeOnly;
    /** Batch owner (server thread) only. */
    @Unique
    private int bons$batchEpoch;
    @Unique
    private boolean bons$unflushed;

    @Override
    public void bons$writeOnly(boolean value) {
        this.bons$writeOnly = value;
    }

    @Override
    public int bons$batchEpoch() {
        return this.bons$batchEpoch;
    }

    @Override
    public void bons$batchEpoch(int epoch) {
        this.bons$batchEpoch = epoch;
    }

    @Override
    public boolean bons$unflushed() {
        return this.bons$unflushed;
    }

    @Override
    public void bons$unflushed(boolean value) {
        this.bons$unflushed = value;
    }

    @WrapOperation(method = "m_243124_", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/network/Connection;m_129520_(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketSendListener;)V"))
    private void bons$markBatched(Connection self, Packet<?> packet, PacketSendListener listener, Operation<Void> original) {
        if (listener != null || !FlushBatch.mark(self, packet)) {
            original.call(self, packet, listener);
            return;
        }
        try {
            original.call(self, packet, listener);
        } finally {
            FlushBatch.unmark();
        }
    }

    @WrapOperation(method = "m_129520_", at = @At(value = "INVOKE", target = "Lio/netty/channel/EventLoop;execute(Ljava/lang/Runnable;)V"))
    private void bons$handOverWithoutWakeup(EventLoop loop, Runnable task, Operation<Void> original) {
        if (!FlushBatch.handOver((Connection) (Object) this, loop, task)) original.call(loop, task);
    }

    @WrapOperation(method = "m_243087_", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/Connection;m_129498_(Lnet/minecraft/network/ConnectionProtocol;)V"))
    private void bons$protocolSwitchFlushes(Connection self, ConnectionProtocol protocol, Operation<Void> original) {
        this.bons$writeOnly = false;
        original.call(self, protocol);
    }

    @WrapOperation(method = "m_243087_", at = @At(value = "INVOKE", target = "Lio/netty/channel/Channel;writeAndFlush(Ljava/lang/Object;)Lio/netty/channel/ChannelFuture;"))
    private ChannelFuture bons$writeWithoutFlush(Channel channel, Object message, Operation<ChannelFuture> original) {
        if (!this.bons$writeOnly) return original.call(channel, message);
        this.bons$writeOnly = false;
        return channel.write(message);
    }

    @WrapOperation(method = "m_129483_", at = @At(value = "INVOKE", target = "Lio/netty/channel/Channel;flush()Lio/netty/channel/Channel;"))
    private Channel bons$tickFlush(Channel channel, Operation<Channel> original) {
        Channel result = original.call(channel);
        FlushBatch.flushedByMinecraft((Connection) (Object) this);
        return result;
    }

    @WrapMethod(method = "m_129483_")
    private void bons$batchOwnTick(Operation<Void> original) {
        boolean mine = FlushBatch.begin((Connection) (Object) this);
        try {
            original.call();
        } finally {
            if (mine) FlushBatch.end();
        }
    }
}
