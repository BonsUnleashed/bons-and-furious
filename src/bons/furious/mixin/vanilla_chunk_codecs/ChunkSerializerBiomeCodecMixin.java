package bons.furious.mixin.vanilla_chunk_codecs;

import bons.furious.patch.vanilla_chunk_codecs.BiomeCodecMemo;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.serialization.Codec;
import net.minecraft.core.Registry;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;
import org.spongepowered.asm.mixin.Mixin;

/**
 * vanilla_chunk_biome_codec_memo (Minecraft 1.20.1 on Forge 47.4.16, both sides): ChunkSerializer.makeBiomeCodec
 * (m_188260_, private static, called once per chunk by write m_63454_ and read m_188230_) answers with the codec it built
 * for the same registry and plains holder before (BiomeCodecMemo). The original still builds every codec that is used.
 * Corgilib, Oh The Trees You'll Grow, Tectonic, Architectury, Block Swap and C2ME's chunkio module inject into write/read
 * at other points; none targets m_188260_.
 */
@Mixin(value = ChunkSerializer.class, remap = false)
public abstract class ChunkSerializerBiomeCodecMixin {
    @WrapMethod(method = "m_188260_")
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Codec bons$keepBiomeCodec(Registry<Biome> registry, Operation<Codec> original) {
        return BiomeCodecMemo.codec(registry, original);
    }
}
