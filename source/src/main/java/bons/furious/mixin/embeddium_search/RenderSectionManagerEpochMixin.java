package bons.furious.mixin.embeddium_search;

import bons.furious.patch.embeddium_search.SearchReplay;
import org.embeddedt.embeddium.impl.render.chunk.RenderSection;
import org.embeddedt.embeddium.impl.render.chunk.RenderSectionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * embeddium_search_replay (Embeddium 1.0.15+mc1.21.1, first written for 0.3.31+mc1.20.1; client only): the section
 * graph's link epoch. Ported to 1.21.1: names only (connect/disconnectNeighborNodes unchanged; RenderSection.setAdjacentNode
 * is still called only from them, class scan of the 1.0.15 jar).
 *
 * A section search follows each section's neighbour links (RenderSection.setAdjacentNode), and in Embeddium those change
 * only in RenderSectionManager.connectNeighborNodes / disconnectNeighborNodes (a section loaded or unloaded; class-file
 * scan of every client jar: no other caller). Each call bumps SearchReplay.graphEpoch, which every recorded search
 * carries, so no recording is replayed across a change of the graph. Runs on the render thread, as both methods do.
 * Nothing in the two methods changes.
 */
@Mixin(value = RenderSectionManager.class, remap = false)
public abstract class RenderSectionManagerEpochMixin {
    @Inject(method = {"connectNeighborNodes", "disconnectNeighborNodes"}, at = @At("HEAD"))
    private void bons$graphChanged(RenderSection render, CallbackInfo ci) {
        SearchReplay.graphEpoch++;
    }
}
