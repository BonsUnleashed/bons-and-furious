package bons.furious.mixin.chunksending;

import bons.furious.patch.chunksending.ChunkSendingExpiry;
import com.chunksending.chunk.IChunkPacketCache;
import com.chunksending.event.EventHandler;
import java.util.Map;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * chunksending_expiry_queue (ChunkSending 3.7): addToClear also notes each deadline in ChunkSendingExpiry's FIFO, and the
 * per-tick sweep pops only the due records there instead of walking every cached chunk. ChunkSending is All Rights
 * Reserved, so only these two injectors and our own helper are used; none of its code is carried. Once per tick and once
 * per cached packet: @Inject is fine.
 */
@Mixin(value = EventHandler.class, remap = false)
public abstract class ExpiryQueueMixin {
    @Shadow
    @Final
    private static Map<IChunkPacketCache, Long> packetCachesToClear;

    @Inject(method = "addToClear", at = @At("TAIL"))
    private static void bons$noteDeadline(IChunkPacketCache cache, CallbackInfo ci) {
        ChunkSendingExpiry.added(packetCachesToClear, cache);
    }

    @Inject(method = "clearExpiredPacketCaches", at = @At("HEAD"), cancellable = true)
    private static void bons$expireDue(CallbackInfo ci) {
        if (ChunkSendingExpiry.expire(packetCachesToClear)) ci.cancel();
    }
}
