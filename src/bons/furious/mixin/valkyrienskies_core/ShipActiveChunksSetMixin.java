package bons.furious.mixin.valkyrienskies_core;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.core.impl.chunk_tracking.ShipActiveChunksSet;

/**
 * valkyrien_chunk_set_version (Valkyrien Skies 2.4.11, VS core ShipActiveChunksSet).
 *
 * Gives every ship's active-chunk set a modification counter. add and remove bump acVsMods once the underlying set
 * call has returned, whether or not it changed the set. The entity collision helper (AcVsSweep7, selected by
 * ChunkSetGate when these public fields exist) caches the set's chunk extents in acVsExtents and the transformed box
 * in acVsWorld, and rebuilds them only when the counter has moved. Nothing else about the set changes.
 */
@Mixin(value = ShipActiveChunksSet.class, remap = false)
public abstract class ShipActiveChunksSetMixin {
    /** Modification counter, read by AcVsSweep7 and looked up by name (Class.getField) by ChunkSetGate. */
    @Unique
    public transient long acVsMods;
    /** AcVsSweep7's cached chunk extents for the counter value they were computed at. */
    @Unique
    public transient Object acVsExtents;
    /** AcVsSweep7's cached world-space box of the ship's chunks. */
    @Unique
    public transient Object acVsWorld;

    @ModifyReturnValue(method = {"add(II)Z", "remove(II)Z"}, at = @At("RETURN"), require = 2)
    private boolean bons$countModification(boolean changed) {
        this.acVsMods++;
        return changed;
    }
}
