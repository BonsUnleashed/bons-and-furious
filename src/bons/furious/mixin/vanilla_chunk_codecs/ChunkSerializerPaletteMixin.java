package bons.furious.mixin.vanilla_chunk_codecs;

import bons.furious.patch.vanilla_chunk_codecs.PaletteDirectNbt;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * vanilla_chunk_palette_direct_nbt (Minecraft 1.20.1 on Forge 47.4.16, both sides): every Codec.encodeStart call in
 * ChunkSerializer.write (m_63454_) goes through PaletteDirectNbt.encodeStart, which builds the block-states and biomes
 * section tags directly for palettes whose every entry and outer shape were verified against the real codec's output,
 * and calls the original otherwise (always for the blending-data and below-zero-retrogen calls, and for anything not
 * NbtOps). Each result goes straight into getOrThrow, as before. Corgilib, Oh The Trees You'll Grow and Tectonic inject
 * into m_63454_ at other points; C2ME's chunkio redirects other calls in it. No Minecraft code.
 */
@Mixin(value = ChunkSerializer.class, remap = false)
public abstract class ChunkSerializerPaletteMixin {
    @WrapOperation(method = "m_63454_", at = @At(value = "INVOKE",
            target = "Lcom/mojang/serialization/Codec;encodeStart(Lcom/mojang/serialization/DynamicOps;Ljava/lang/Object;)Lcom/mojang/serialization/DataResult;"))
    @SuppressWarnings("rawtypes")
    private static DataResult bons$directPalette(Codec codec, DynamicOps ops, Object input, Operation<DataResult> original) {
        return PaletteDirectNbt.encodeStart(codec, ops, input, original);
    }
}
