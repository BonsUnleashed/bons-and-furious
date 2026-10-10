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
 * vanilla_chunk_biome_codec_memo (Minecraft 1.21.1 with NeoForge 21.1.252, both sides): ChunkSerializer.makeBiomeCodec
 * (private static, called once per chunk by write and read) answers with the codec it built for the same registry and
 * plains holder before (BiomeCodecMemo). The original still builds every codec that is used. In the pinned 1.21.1 targets
 * Architectury (read: HEAD, the chunk event) and C2ME's async_serialization (read/write: other calls) inject into
 * ChunkSerializer; none targets makeBiomeCodec.
 *
 * Ported to 1.21.1: unchanged (makeBiomeCodec is the same code: codecRO over asHolderIdMap, holderByNameCodec, SECTION_BIOMES
 * and getHolderOrThrow(PLAINS)).
 */
@Mixin(value = ChunkSerializer.class, remap = false)
public abstract class ChunkSerializerBiomeCodecMixin {
    @WrapMethod(method = "makeBiomeCodec")
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Codec bons$keepBiomeCodec(Registry<Biome> registry, Operation<Codec> original) {
        return BiomeCodecMemo.codec(registry, original);
    }
}
