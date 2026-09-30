package bons.furious.mixin.embeddium;

import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import me.jellysquid.mods.sodium.client.gl.arena.GlBufferArena;
import me.jellysquid.mods.sodium.client.gl.arena.PendingUpload;
import me.jellysquid.mods.sodium.client.gl.buffer.GlBuffer;
import me.jellysquid.mods.sodium.client.gl.buffer.GlMutableBuffer;
import me.jellysquid.mods.sodium.client.gl.device.CommandList;
import me.jellysquid.mods.sodium.client.util.NativeBuffer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

/**
 * embeddium_pending_upload_sum (Embeddium 0.3.31+mc1.20.1).
 *
 * When the arena cannot place every pending upload, GlBufferArena.upload sizes the resize from the bytes still queued,
 * which it summed with queue.stream().mapToLong(...).sum() on the render thread. For the plain PendingUpload and
 * NativeBuffer objects Embeddium itself creates, the same sum is now taken with a loop; a list holding anything else
 * (null, a subclass) still takes the original stream path, so the result and any exception are unchanged.
 */
@Mixin(value = GlBufferArena.class, remap = false)
public abstract class GlBufferArenaPendingSumMixin {
    @Shadow private GlMutableBuffer arenaBuffer;
    @Shadow @Final private int stride;

    @Shadow
    private void tryUploads(CommandList commandList, List<PendingUpload> queue) {
        // shadowed: the body is the installed method's
    }

    @Shadow
    public abstract void ensureCapacity(CommandList commandList, int elementCount);

    /**
     * @author BonsUnleashed
     * @reason Size the resize with ac$pendingBytes (a loop) instead of a stream pipeline.
     */
    @Overwrite
    public boolean upload(CommandList commandList, Stream<PendingUpload> stream) {
        GlBuffer buffer = this.arenaBuffer;
        List<PendingUpload> queue = stream.collect(Collectors.toCollection(LinkedList::new));
        this.tryUploads(commandList, queue);
        if (!queue.isEmpty()) {
            int remainingElements = (int) (ac$pendingBytes(queue) / this.stride);
            this.ensureCapacity(commandList, remainingElements);
            this.tryUploads(commandList, queue);
            if (!queue.isEmpty()) {
                throw new RuntimeException("Failed to upload all buffers");
            }
        }
        return this.arenaBuffer != buffer;
    }

    /** Bytes still queued; the loop only runs when every element is exactly Embeddium's own PendingUpload/NativeBuffer. */
    @Unique
    private static long ac$pendingBytes(List<PendingUpload> queue) {
        for (PendingUpload upload : queue) {
            if (upload == null || upload.getClass() != PendingUpload.class || upload.getDataBuffer() == null
                    || upload.getDataBuffer().getClass() != NativeBuffer.class) {
                return queue.stream().mapToLong(pending -> pending.getDataBuffer().getLength()).sum();
            }
        }
        long bytes = 0L;
        for (PendingUpload upload : queue) {
            bytes += upload.getDataBuffer().getLength();
        }
        return bytes;
    }
}
