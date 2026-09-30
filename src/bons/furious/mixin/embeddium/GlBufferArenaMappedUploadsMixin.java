package bons.furious.mixin.embeddium;

import bons.pure.optimizations.MappedUploads;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Function;
import me.jellysquid.mods.sodium.client.gl.arena.GlBufferArena;
import me.jellysquid.mods.sodium.client.gl.arena.PendingUpload;
import me.jellysquid.mods.sodium.client.gl.buffer.GlMutableBuffer;
import me.jellysquid.mods.sodium.client.gl.device.CommandList;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

/**
 * embeddium_direct_upload_preparation, arena half (Embeddium 0.3.31+mc1.20.1).
 *
 * RenderRegionManager handed every arena upload over as a Stream (list.stream().map(...), sometimes .filter(nonNull))
 * that upload() immediately collected back into a LinkedList. GlBufferArena now also implements MappedUploads: its
 * bons$uploadMapped takes the list and the mapping function directly and fills the LinkedList itself, then runs the same
 * steps as upload() (try, grow by the bytes still queued, try again, fail if anything is left) with the same result.
 * The region half (RenderRegionManagerDirectUploadMixin) calls it.
 */
@Mixin(value = GlBufferArena.class, remap = false)
public abstract class GlBufferArenaMappedUploadsMixin implements MappedUploads {
    @Shadow private GlMutableBuffer arenaBuffer;
    @Shadow @Final private int stride;

    @Shadow
    private void tryUploads(CommandList commandList, List<PendingUpload> queue) {
        // shadowed: the body is the installed method's
    }

    @Shadow
    public abstract void ensureCapacity(CommandList commandList, int elementCount);

    /** upload(commandList, inputs.stream().map(mapper)[.filter(Objects::nonNull)]) without building the stream. */
    @Unique
    @Override
    @SuppressWarnings("unchecked")
    public boolean bons$uploadMapped(Object commands, List<?> inputs, Function<?, ?> mapper, boolean omitNull) {
        CommandList commandList = (CommandList) commands;
        GlMutableBuffer buffer = this.arenaBuffer;
        List<PendingUpload> queue = new LinkedList<>();
        for (Object input : inputs) {
            PendingUpload upload = (PendingUpload) ((Function<Object, ?>) mapper).apply(input);
            if (!omitNull || upload != null) {
                queue.add(upload);
            }
        }
        this.tryUploads(commandList, queue);
        if (!queue.isEmpty()) {
            long bytes = 0L;
            for (PendingUpload upload : queue) {
                bytes += upload.getDataBuffer().getLength();
            }
            int remainingElements = (int) (bytes / this.stride);
            this.ensureCapacity(commandList, remainingElements);
            this.tryUploads(commandList, queue);
            if (!queue.isEmpty()) {
                throw new RuntimeException("Failed to upload all buffers");
            }
        }
        return this.arenaBuffer != buffer;
    }
}
