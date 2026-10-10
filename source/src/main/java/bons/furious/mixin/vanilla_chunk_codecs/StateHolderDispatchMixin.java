package bons.furious.mixin.vanilla_chunk_codecs;

import bons.furious.patch.vanilla_chunk_codecs.StateCodecMemo;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.serialization.Codec;
import java.util.function.Function;
import net.minecraft.world.level.block.state.StateHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * vanilla_state_codec_dispatch_memo (Minecraft 1.21.1 with NeoForge 21.1.252, both sides): StateHolder.codec hands
 * Codec.dispatch("Name", ..., codecFunction) a wrapped codec function (StateCodecMemo.wrap) that keeps the per-block codec
 * the original function builds instead of rebuilding it for every encoded or decoded block state. Only the call made by
 * BlockState.<clinit> (BlockState.CODEC = codec(BuiltInRegistries.BLOCK.byNameCodec(), Block::defaultBlockState)) is
 * wrapped; StateCodecMemo.wrap returns any other caller's function unchanged (FluidState.<clinit> is the only other caller
 * in vanilla). The wrap happens once per call of codec. No Minecraft code: the original function still builds every
 * codec that is used. Why identical: StateCodecMemo.
 *
 * Ported to 1.21.1: the dispatched function (lambda$codec$2) now returns a MapCodec (DFU 8's dispatch takes a
 * Function to MapCodec); the wrapped call, Codec.dispatch(String, Function, Function), has the same descriptor.
 */
@Mixin(value = StateHolder.class, remap = false)
public abstract class StateHolderDispatchMixin {
    @WrapOperation(method = "codec", at = @At(value = "INVOKE",
            target = "Lcom/mojang/serialization/Codec;dispatch(Ljava/lang/String;Ljava/util/function/Function;Ljava/util/function/Function;)Lcom/mojang/serialization/Codec;"))
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Codec bons$keepStateCodecs(Codec ownerCodec, String typeKey, Function type, Function codecFunction,
                                              Operation<Codec> original) {
        return original.call(ownerCodec, typeKey, type, StateCodecMemo.wrap(codecFunction));
    }
}
