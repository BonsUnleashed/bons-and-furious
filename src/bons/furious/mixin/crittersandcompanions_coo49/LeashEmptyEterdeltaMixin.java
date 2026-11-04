package bons.furious.mixin.crittersandcompanions_coo49;

import bons.furious.patch.crittersandcompanions_coo49.LeashSkip;
import com.github.eterdelta.crittersandcompanions.client.renderer.SilkLeashRenderer;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.Iterator;
import java.util.Set;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/**
 * crittersandcompanions_leash_render_empty_skip, Critters and Companions 2.3.5 (package com.github.eterdelta; absent in
 * 2.7.1 packs, so the mixin is @Pseudo and CrittersandcompanionsPlugin offers it to Mixin only when the guard matched the
 * 2.3.5 classes), client: the one Set.iterator() call in SilkLeashRenderer.renderSilkLeash goes through LeashSkip.
 */
@Pseudo
@Mixin(value = SilkLeashRenderer.class, remap = false)
public abstract class LeashEmptyEterdeltaMixin {
    @WrapOperation(method = "renderSilkLeash", at = @At(value = "INVOKE", target = "Ljava/util/Set;iterator()Ljava/util/Iterator;"), require = 1, allow = 1)
    private static Iterator<?> bons$emptyLeashLoop(Set<?> set, Operation<Iterator<?>> original) {
        return LeashSkip.iterator(set, original);
    }
}
