package bons.furious.mixin.vanilla_leveldat_encode;

import bons.furious.patch.vanilla_leveldat_encode.ParameterListEncodes;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.mojang.serialization.Codec;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * vanilla_leveldat_encode_reuse (Minecraft 1.20.1, server side; SRG names).
 *
 * Climate.ParameterList.codec(entryCodec) builds the codec of a biome parameter list (MultiNoiseBiomeSource's inline
 * "biomes" list uses it, and so does any mod that builds one). The returned codec is wrapped by ParameterListEncodes: decode
 * is passed through unchanged; an encode of an unchanged large list through level.dat's RegistryOps over NbtOps reads its
 * kept encoding back instead of encoding every entry again. ParameterListEncodes explains why the saved bytes are the same.
 * Runs once per codec creation (class initialisation).
 */
@Mixin(targets = "net.minecraft.world.level.biome.Climate$ParameterList", remap = false)
public abstract class ParameterListCodecMixin {
    @ModifyReturnValue(method = "codec(Lcom/mojang/serialization/MapCodec;)Lcom/mojang/serialization/Codec;", at = @At("RETURN"))
    @SuppressWarnings("rawtypes")
    private static Codec bons$keepLargeListEncodings(Codec codec) {
        return ParameterListEncodes.wrapAny(codec);
    }
}
