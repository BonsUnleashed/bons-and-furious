package bons.furious.mixin.oculus_outline;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * oculus_shadow_outline_discard (Minecraft 1.21.1 ByteBufferBuilder, client only): read access to the allocated capacity
 * and the write offset of the outline source's shared byte buffer, for SHADOW mode's BYTES_HELD (the growth the bug
 * causes). Accessor only. New in the 1.21.1 port (1.20.1 read BufferBuilder's ByteBuffer by reflection; the 1.21 byte
 * buffer is a native pointer with an int capacity).
 */
@Mixin(value = ByteBufferBuilder.class, remap = false)
public interface ByteBufferBuilderAccessor {
    @Accessor("capacity")
    int bons$capacity();

    @Accessor("writeOffset")
    int bons$writeOffset();
}
