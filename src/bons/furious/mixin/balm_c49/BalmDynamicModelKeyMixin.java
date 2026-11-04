package bons.furious.mixin.balm_c49;

import bons.furious.patch.balm_c49.BalmMemos;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.blay09.mods.balm.common.client.rendering.AbstractCachedDynamicModel;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * balm_dynamic_model_state_key (Balm 7.3.44, client): MixinExtras @WrapOperation on the BlockState.toString() call in
 * AbstractCachedDynamicModel.getQuads (m_213637_), answered with the remembered text of that state (BalmMemos). Injector only.
 */
@Mixin(value = AbstractCachedDynamicModel.class, remap = false)
public abstract class BalmDynamicModelKeyMixin {
    @WrapOperation(method = "m_213637_", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/state/BlockState;toString()Ljava/lang/String;"))
    private String bons$balmStateKey(BlockState state, Operation<String> original) {
        return BalmMemos.stateKey(state, original);
    }
}
