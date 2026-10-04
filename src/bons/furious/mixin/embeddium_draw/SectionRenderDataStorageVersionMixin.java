package bons.furious.mixin.embeddium_draw;

import bons.furious.patch.embeddium_draw.DrawBatchCache;
import me.jellysquid.mods.sodium.client.render.chunk.data.SectionRenderDataStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * embeddium_draw_batch_cache (Embeddium 0.3.31+mc1.20.1; client only): the render-data version of each storage.
 *
 * A SectionRenderDataStorage's native render data (per section: slice mask, and per facing vertex offset, element count,
 * index offset) is written only by its own methods setMeshes, removeMeshes, replaceIndexBuffer and onBufferResized
 * (class-file scan of every client jar: SectionRenderDataUnsafe's setters are called from nowhere else); removeIndexBuffer
 * and delete release segments. Each of the six bumps the storage's version at its head, so a remembered command list
 * (DrawBatchCache.Entry) never outlives the data it was filled from. The storage also carries the two remembered lists.
 * These calls happen when meshes are uploaded or regions change, not per frame. Nothing in the methods changes.
 */
@Mixin(value = SectionRenderDataStorage.class, remap = false)
public abstract class SectionRenderDataStorageVersionMixin implements DrawBatchCache.Host {
    @Unique
    private final DrawBatchCache.Slots bons$drawSlots = new DrawBatchCache.Slots();

    @Override
    public DrawBatchCache.Slots bons$drawSlots() {
        return this.bons$drawSlots;
    }

    @Inject(method = {"setMeshes", "removeMeshes", "removeIndexBuffer", "replaceIndexBuffer", "onBufferResized", "delete"}, at = @At("HEAD"))
    private void bons$renderDataChanged(CallbackInfo ci) {
        this.bons$drawSlots.version++;
    }
}
