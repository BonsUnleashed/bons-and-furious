package bons.furious.patch.fluidwalk_c2;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch relics_fluid_walk_scan (Relics; 1.21.1 tested build relics-1.21.1-0.10.7.8; both sides).
 * Relics is All Rights Reserved: none of its code is carried here; Relics' handler itself runs for every case this
 * pre-check does not settle.
 *
 * Relics' EntityMixin.fluidCollision (a @ModifyVariable on Entity.move after collide(), run for every living entity
 * that is not moving up) builds an offset table (13 int arrays) and one BlockPos per offset on every call, about 0.75 KB,
 * reads the fluid state of 12 cells around the entity, and only when one of them holds a fluid goes on to compare heights
 * and post FluidCollisionEvent. When none of the 12 cells holds a fluid, it returns the vector it was given, unchanged.
 *
 * {@link #dryEverywhere} is our own check of exactly that case: it reads the same 12 cells, in the handler's order,
 * through the same Level.getFluidState, with the entity's per-entity cursor instead of new objects. If every cell is
 * empty, the handler's answer is known (the original vector, no event, no other effect) and the mixin skips it; the
 * reads that happened are exactly the handler's own, in the same order, so chunk loading, tickets and caches see the
 * same calls. If a cell holds a fluid, the handler runs unchanged and reads the cells again. To keep those repeated reads
 * free of effects, the check only runs when all 12 cells lie in the column of the entity's own block (x and z not on the
 * chunk's first or last row): a moving entity stands in a loaded chunk, so its reads there load nothing, run no other
 * code and are cache hits on the second pass. Elsewhere (and while the entity moves up, which the handler returns on
 * before reading anything) the handler runs as before.
 *
 * Ported to 1.21.1: Relics 0.10.7.8's handler (javap against 0.8.0.13) reads the same 12 cells in the same order with
 * the same early returns; the chunk-cache argument holds for vanilla 1.21.1 ServerChunkCache.getChunk and for Radium
 * 0.13.1's chunk_access overwrite (both answer a cached chunk first).
 */
public final class RelicsFluidWalk {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    /** Runtime switch. -Dbons_and_furious.relicsFluidWalkScan=false hands every call to Relics' own code. */
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.relicsFluidWalkScan", "true"));
    /** The cells Relics' handler probes, in its order, as offsets from the entity's block position. */
    private static final int[] DX = {1, 1, 1, 1, 0, 0, 0, 0, -1, -1, -1, -1};
    private static final int[] DY = {0, 0, -1, 0, 0, 0, -1, 0, 0, 0, -1, 0};
    private static final int[] DZ = {1, 0, 0, -1, 1, 0, 0, -1, 1, 0, 0, -1};
    private static volatile boolean announced;

    private RelicsFluidWalk() {
    }

    /** True when Relics' handler would read only empty fluid states here, so its answer is the original vector. */
    public static boolean dryEverywhere(LivingEntity entity, Vec3 original, FluidWalkCursor cursor) {
        if (original.y > 0.0) return false;               // the handler returns before reading anything
        BlockPos first = entity.blockPosition();
        int lx = first.getX() & 15, lz = first.getZ() & 15;
        if (lx == 0 || lx == 15 || lz == 0 || lz == 15) return false;
        Level level = entity.getCommandSenderWorld();
        for (int i = 0; i < 12; i++) {
            BlockPos source = entity.blockPosition();
            if (source != first) return false;                    // cannot happen between reads of a loaded chunk
            if (!level.getFluidState(cursor.set(source.getX() + DX[i], source.getY() + DY[i], source.getZ() + DZ[i])).isEmpty()) {
                return false;
            }
        }
        if (!announced) announce();
        return true;
    }

    private static synchronized void announce() {
        if (announced) return;
        announced = true;
        LOGGER.info("Bons and Furious: relics_fluid_walk_scan: Relics' fluid-walk check is skipped where its cells hold no fluid");
    }
}
