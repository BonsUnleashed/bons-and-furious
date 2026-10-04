package bons.furious.mixin.distanthorizons_c2;

import bons.furious.patch.distanthorizons_c2.BiomeCacheReset;
import com.seibel.distanthorizons.core.api.internal.SharedApi;
import com.seibel.distanthorizons.core.world.AbstractDhWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * distanthorizons_world_change_biome_reset (Distant Horizons, LGPL-3.0; 1.21.1 tested build
 * DistantHorizons-3.3.3-1.21.1-fabric-neoforge; both sides; a fix).
 *
 * Two injections into SharedApi.setDhWorld, both inside its world lock (see {@link BiomeCacheReset}): after Distant
 * Horizons' own BlockTextureRegistry.clear() in the unload branch (before its System.gc(), so the old registries can be
 * collected at once), and right before ThreadPoolUtil.setupThreadPools() in the load branch (after the previous world, if
 * any, was closed). setDhWorld runs once per world change, so the CallbackInfo of @Inject costs nothing measurable.
 *
 * Ported to 1.21.1: DH 3.3.3's setDhWorld has the same two branches and both anchors (decompiled side by side).
 */
@Mixin(value = SharedApi.class, remap = false)
public abstract class SharedApiWorldChangeMixin {
    @Inject(method = "setDhWorld", require = 1, allow = 1, at = @At(value = "INVOKE", shift = At.Shift.AFTER,
            target = "Lcom/seibel/distanthorizons/core/dataObjects/render/textures/BlockTextureRegistry;clear()V"))
    private static void bons$biomeCachesAfterUnload(AbstractDhWorld newWorld, CallbackInfo ci) {
        BiomeCacheReset.reset("unload");
    }

    @Inject(method = "setDhWorld", require = 1, allow = 1, at = @At(value = "INVOKE",
            target = "Lcom/seibel/distanthorizons/core/util/threading/ThreadPoolUtil;setupThreadPools()V"))
    private static void bons$biomeCachesBeforeLoad(AbstractDhWorld newWorld, CallbackInfo ci) {
        BiomeCacheReset.reset("load");
    }
}
