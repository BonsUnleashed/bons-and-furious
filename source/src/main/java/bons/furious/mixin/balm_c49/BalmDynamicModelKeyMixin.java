package bons.furious.mixin.balm_c49;

import bons.furious.patch.balm_c49.BalmMemos;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * balm_dynamic_model_state_key (Balm 21.0.67 for NeoForge 1.21.1, client): MixinExtras @WrapOperation on the one
 * BlockState.toString() call in AbstractCachedDynamicModel.getQuads(BlockState, Direction, RandomSource) (NeoForge's
 * five-argument getQuads reaches it through IBakedModelExtension's default; NeoForgeCachedDynamicModel does not override
 * it), answered with the remembered text of that state (BalmMemos). Injector only.
 */
@Mixin(targets = "net.blay09.mods.balm.common.client.rendering.AbstractCachedDynamicModel", remap = false)
public abstract class BalmDynamicModelKeyMixin {
    @WrapOperation(method = "getQuads(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/Direction;Lnet/minecraft/util/RandomSource;)Ljava/util/List;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;toString()Ljava/lang/String;"))
    private String bons$balmStateKey(BlockState state, Operation<String> original) {
        return BalmMemos.stateKey(state, original);
    }
}
