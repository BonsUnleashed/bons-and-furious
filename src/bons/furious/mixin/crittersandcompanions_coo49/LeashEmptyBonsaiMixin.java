package bons.furious.mixin.crittersandcompanions_coo49;

import bons.furious.patch.crittersandcompanions_coo49.LeashSkip;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.Iterator;
import java.util.Set;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/**
 * crittersandcompanions_leash_render_empty_skip, Critters and Companions 2.7.1 (package io.github.bonsaistudi0s, not on the
 * build's class path: string target + @Pseudo, offered to Mixin by CrittersandcompanionsPlugin only when the guard matched
 * the 2.7.1 classes), client: the one Set.iterator() call in SilkLeashRenderer.renderSilkLeash goes through LeashSkip.
 */
@Pseudo
@Mixin(targets = "io.github.bonsaistudi0s.crittersandcompanions.client.renderer.SilkLeashRenderer", remap = false)
public abstract class LeashEmptyBonsaiMixin {
    @WrapOperation(method = "renderSilkLeash", at = @At(value = "INVOKE", target = "Ljava/util/Set;iterator()Ljava/util/Iterator;"), require = 1, allow = 1)
    private static Iterator<?> bons$emptyLeashLoop(Set<?> set, Operation<Iterator<?>> original) {
        return LeashSkip.iterator(set, original);
    }
}
