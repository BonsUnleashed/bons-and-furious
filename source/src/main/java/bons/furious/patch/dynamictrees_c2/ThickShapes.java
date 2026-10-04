package bons.furious.patch.dynamictrees_c2;

import com.mojang.logging.LogUtils;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.slf4j.Logger;

/**
 * Bons and Furious switch dynamictrees_thick_shape_memo (Dynamic Trees, MIT; 1.21.1 tested build:
 * dynamictrees-neoforge-1.21.1-1.7.2; both sides). No Dynamic Trees code.
 *
 * Each of the eight TrunkShellBlocks around a thick trunk block (ThickBranchBlock, radius 9..24) answers every shape query
 * with Shapes.create(coreShape.bounds().move(offset to the core)): a new ArrayVoxelShape with three coordinate lists per
 * query (entity collisions near big trunks, the crosshair ray and outline, pathfinding clearance), and every new shape
 * misses vanilla's identity-keyed Block.isShapeFullBlock cache.
 *
 * Shapes.create is a pure function of the box's six doubles. Here a box is answered from a table when it is exactly (all
 * six doubles equal) the box such a block builds: the core box of radius r in 9..24 moved by an offset in {-1, 0, 1}^3,
 * computed with the same double operations as Dynamic Trees and AABB.move. The table holds Shapes.create of that same
 * box, built once, so the answer has the same geometry; only the object is shared (VoxelShape is immutable; identity-keyed
 * caches now hit). Any other box goes to Shapes.create as before.
 *
 * Ported to 1.21.1: Dynamic Trees 1.7.2 already builds the thick core shapes once (ThickBranchBlock.precomputeTrunkShapes,
 * getShape returns trunkShapes[radius - 1]), so the 1.20.1 hook in ThickBranchBlock.getShape has no call to answer and is
 * not shipped; the shells still create a shape per query (TrunkShellBlock lambda$getShape$3). The core box doubles are
 * unchanged ((i + 1) / 16.0 around 0.5, exactly the 1.20.1 values), and a radius 9..24 box lies outside the unit cell, so
 * Shapes.create keeps its exact coordinates and bounds() gives them back.
 */
public final class ThickShapes {
    /** Runtime switch. -Dbons_and_furious.dynamictreesThickShapeMemo=false turns it off. */
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.dynamictreesThickShapeMemo", "true"));
    /** Index: (r - 9) * 27 + (dx + 1) * 9 + (dy + 1) * 3 + (dz + 1). Filled on first use; a racing duplicate is an equal shape. */
    private static final VoxelShape[] TABLE = new VoxelShape[16 * 27];
    private static final Logger LOGGER = LogUtils.getLogger();
    private static volatile boolean announced;

    private ThickShapes() {
    }

    /** The table's shape for this box, or null when the box is not a thick trunk or shell box. */
    public static VoxelShape memo(AABB box) {
        double minY = box.minY;
        int dy = (int) minY;
        if (minY != dy || dy < -1 || dy > 1 || box.maxY != 1.0 + dy) {
            return null;
        }
        long r = Math.round((box.maxX - box.minX) * 8.0);
        if (r < 9 || r > 24) {
            return null;
        }
        double h = r / 16.0;
        double lo = 0.5 - h, hi = 0.5 + h;
        long dxl = Math.round(box.minX - lo), dzl = Math.round(box.minZ - lo);
        if (dxl < -1 || dxl > 1 || dzl < -1 || dzl > 1) {
            return null;
        }
        int dx = (int) dxl, dz = (int) dzl;
        // the exact doubles Dynamic Trees builds (core: offset 0) and AABB.move adds (shells: + (double) offset)
        if (box.minX != lo + dx || box.maxX != hi + dx || box.minZ != lo + dz || box.maxZ != hi + dz) {
            return null;
        }
        int i = ((int) r - 9) * 27 + (dx + 1) * 9 + (dy + 1) * 3 + (dz + 1);
        VoxelShape s = TABLE[i];
        if (s == null) {
            s = Shapes.create(new AABB(lo + dx, 0.0 + dy, lo + dz, hi + dx, 1.0 + dy, hi + dz));
            TABLE[i] = s;
            if (!announced) {
                announced = true;
                LOGGER.info("Bons and Furious: dynamictrees_thick_shape_memo: trunk shell shapes are built once per radius and offset");
            }
        }
        return s;
    }
}
