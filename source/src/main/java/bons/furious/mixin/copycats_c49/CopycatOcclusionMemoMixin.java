package bons.furious.mixin.copycats_c49;

import bons.furious.patch.copycats_c49.OcclusionMemo;
import com.bawnorton.mixinsquared.TargetHandler;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockBehaviour;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * copycats_occlusion_holder_memo (Copycats+ 3.0.9+mc.1.21.1, both sides): a MixinSquared @TargetHandler @Redirect of the
 * Holder.is(ResourceLocation) call inside Copycats+' BlockStateBaseMixin.customOcclusion handler (merged into
 * BlockStateBase at the start of canOcclude): the answer is remembered per block state in one byte (OcclusionMemo explains
 * why it never changes). Copycats+' other occlusion hook, BlockStateBaseCacheMixin (a WrapOperation on the canOcclude call
 * of BlockStateBase$Cache's constructor, once per state), is not touched. Our own logic only. Priority 1500: after
 * Copycats+' mixin. require = 0: if another mod removed that mixin there is nothing to redirect.
 */
@Mixin(value = BlockBehaviour.BlockStateBase.class, priority = 1500, remap = false)
public abstract class CopycatOcclusionMemoMixin {
    @Unique
    private byte bons$copycats_c49Base;

    @TargetHandler(mixin = "com.copycatsplus.copycats.mixin.foundation.copycat.BlockStateBaseMixin", name = "customOcclusion")
    @Redirect(method = "@MixinSquared:Handler", at = @At(value = "INVOKE", target = "Lnet/minecraft/core/Holder;is(Lnet/minecraft/resources/ResourceLocation;)Z"),
            require = 0, expect = 0, allow = 1)
    private boolean bons$rememberedCopycatBase(Holder<?> holder, ResourceLocation location) {
        int r = OcclusionMemo.is(holder, location, this.bons$copycats_c49Base);
        int remember = r >>> 1;
        if (remember != 0) this.bons$copycats_c49Base = (byte) remember;
        return (r & 1) != 0;
    }
}
