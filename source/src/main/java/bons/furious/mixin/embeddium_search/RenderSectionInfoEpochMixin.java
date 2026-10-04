package bons.furious.mixin.embeddium_search;

import bons.furious.patch.embeddium_search.SearchReplay;
import org.embeddedt.embeddium.impl.render.chunk.RenderSection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * embeddium_search_replay (Embeddium 1.0.15+mc1.21.1, first written for 0.3.31+mc1.20.1; client only): the build-info
 * epoch. Ported to 1.21.1: names only (setRenderState / clearRenderState are still the only writers of flags and
 * visibilityData; 1.0.15's new per-pass translucency sort states are not read by the search).
 *
 * With occlusion culling, a section search reads each visible section's visibility data, which RenderSection keeps from its
 * build info and changes only in setRenderState (new build info) and clearRenderState (info cleared, section deleted).
 * Both bump SearchReplay.infoEpoch at their head, so a recorded main-view search is never replayed across a change of any
 * section's build info. They run when build results are uploaded and when sections unload, on the render thread; nothing
 * in them changes.
 */
@Mixin(value = RenderSection.class, remap = false)
public abstract class RenderSectionInfoEpochMixin {
    @Inject(method = {"setRenderState", "clearRenderState"}, at = @At("HEAD"))
    private void bons$infoChanged(CallbackInfo ci) {
        SearchReplay.infoEpoch++;
    }
}
