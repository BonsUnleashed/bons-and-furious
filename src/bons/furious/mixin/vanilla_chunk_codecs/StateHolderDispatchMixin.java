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
 * vanilla_state_codec_dispatch_memo (Minecraft 1.20.1 on Forge 47.4.16, both sides): StateHolder.codec (m_61127_) hands
 * Codec.dispatch("Name", ..., codecFunction) a wrapped codec function (StateCodecMemo.wrap) that keeps the per-block codec
 * the original function builds instead of rebuilding it for every encoded or decoded block state. Only the call made by
 * BlockState.<clinit> (BlockState.CODEC = codec(Block.CODEC, Block::defaultBlockState)) is wrapped; StateCodecMemo.wrap
 * returns any other caller's function unchanged (FluidState.<clinit> is the only other caller in both instances). The wrap
 * happens once per call of m_61127_. No Minecraft code: the original function still builds every codec that is used.
 * Why identical: StateCodecMemo.
 */
@Mixin(value = StateHolder.class, remap = false)
public abstract class StateHolderDispatchMixin {
    @WrapOperation(method = "m_61127_", at = @At(value = "INVOKE",
            target = "Lcom/mojang/serialization/Codec;dispatch(Ljava/lang/String;Ljava/util/function/Function;Ljava/util/function/Function;)Lcom/mojang/serialization/Codec;"))
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Codec bons$keepStateCodecs(Codec ownerCodec, String typeKey, Function type, Function codecFunction,
                                              Operation<Codec> original) {
        return original.call(ownerCodec, typeKey, type, StateCodecMemo.wrap(codecFunction));
    }
}
