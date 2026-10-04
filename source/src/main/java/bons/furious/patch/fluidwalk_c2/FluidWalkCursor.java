package bons.furious.patch.fluidwalk_c2;

import net.minecraft.core.BlockPos;

/**
 * Bons and Furious switches lionfishapi_fluid_walk_scan and relics_fluid_walk_scan (Minecraft 1.21.1 / NeoForge; both
 * sides): the per-entity read cursor of the fluid-walk scans, so a scan allocates nothing. One cursor per entity and
 * switch, created on the entity's first scan; the scan sets it before every Level.getFluidState call and never reads
 * coordinates back from it (the shape test uses the scan's own ints), so a mod that kept or changed the position it was
 * handed could not alter the result. The scan's best height is returned through {@link #highestValue}; the caller copies
 * it out before posting any event. Ported to 1.21.1: unchanged (BlockPos.MutableBlockPos).
 */
public final class FluidWalkCursor extends BlockPos.MutableBlockPos {
    /** The scan's highest fluid height (stock's local "highestValue"); valid right after FluidWalkScan.scan returns. */
    double highestValue;

    public FluidWalkCursor() {
        super();
    }
}
