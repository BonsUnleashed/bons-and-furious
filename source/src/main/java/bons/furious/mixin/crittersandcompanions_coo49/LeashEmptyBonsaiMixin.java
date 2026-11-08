package bons.furious.mixin.crittersandcompanions_coo49;

import bons.furious.patch.crittersandcompanions_coo49.LeashSkip;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.bonsaistudi0s.crittersandcompanions.client.renderer.SilkLeashRenderer;
import java.util.Iterator;
import java.util.Set;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * crittersandcompanions_leash_render_empty_skip, Critters and Companions 2.7.0 for Minecraft 1.21.1 (package
 * io.github.bonsaistudi0s), client: the one Set.iterator() call in SilkLeashRenderer.renderSilkLeash(Entity, float,
 * PoseStack, MultiBufferSource) (the per-entity pass LivingEntityRendererMixin and GeoEntityRendererMixin call; the
 * five-argument overload that draws one leash has no iterator) goes through LeashSkip.
 */
@Mixin(value = SilkLeashRenderer.class, remap = false)
public abstract class LeashEmptyBonsaiMixin {
    @WrapOperation(method = "renderSilkLeash(Lnet/minecraft/world/entity/Entity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;)V",
            at = @At(value = "INVOKE", target = "Ljava/util/Set;iterator()Ljava/util/Iterator;"), require = 1, allow = 1)
    private static Iterator<?> bons$emptyLeashLoop(Set<?> set, Operation<Iterator<?>> original) {
        return LeashSkip.iterator(set, original);
    }
}
