package bons.furious.patch.dynamictrees_c2;

import com.mojang.logging.LogUtils;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.slf4j.Logger;

/**
 * Bons and Furious switch dynamictrees_thick_shape_memo (Dynamic Trees 1.20.1-1.4.11, MIT; both sides). No Dynamic Trees code.
 *
 * A thick trunk block (ThickBranchBlock, radius 9..24) answers every shape query with Shapes.create(new AABB(0.5 - r/16,
 * 0, 0.5 - r/16, 0.5 + r/16, 1, 0.5 + r/16)), and each of the eight TrunkShellBlocks around it with
 * Shapes.create(coreShape.bounds().move(offset to the core)): a new ArrayVoxelShape with three coordinate lists per query
 * (entity collisions near big trunks, the crosshair ray and outline, pathfinding clearance), and every new shape misses
 * vanilla's identity-keyed Block.isShapeFullBlock cache.
 *
 * Shapes.create is a pure function of the box's six doubles. Here a box is answered from a table when it is exactly (all
 * six doubles equal) the box such a block builds: the core box of radius r in 9..24 moved by an offset in {-1, 0, 1}^3,
 * computed with the same double operations as Dynamic Trees and AABB.move. The table holds Shapes.create of that same
 * box, built once, so the answer has the same geometry; only the object is shared (VoxelShape is immutable; identity-keyed
 * caches now hit). Any other box goes to Shapes.create as before.
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
        double minY = box.f_82289_;
        int dy = (int) minY;
        if (minY != dy || dy < -1 || dy > 1 || box.f_82292_ != 1.0 + dy) {
            return null;
        }
        long r = Math.round((box.f_82291_ - box.f_82288_) * 8.0);
        if (r < 9 || r > 24) {
            return null;
        }
        double h = r / 16.0;
        double lo = 0.5 - h, hi = 0.5 + h;
        long dxl = Math.round(box.f_82288_ - lo), dzl = Math.round(box.f_82290_ - lo);
        if (dxl < -1 || dxl > 1 || dzl < -1 || dzl > 1) {
            return null;
        }
        int dx = (int) dxl, dz = (int) dzl;
        // the exact doubles Dynamic Trees builds (core: offset 0) and AABB.move adds (shells: + (double) offset)
        if (box.f_82288_ != lo + dx || box.f_82291_ != hi + dx || box.f_82290_ != lo + dz || box.f_82293_ != hi + dz) {
            return null;
        }
        int i = ((int) r - 9) * 27 + (dx + 1) * 9 + (dy + 1) * 3 + (dz + 1);
        VoxelShape s = TABLE[i];
        if (s == null) {
            s = Shapes.m_83064_(new AABB(lo + dx, 0.0 + dy, lo + dz, hi + dx, 1.0 + dy, hi + dz));
            TABLE[i] = s;
            if (!announced) {
                announced = true;
                LOGGER.info("Bons and Furious: dynamictrees_thick_shape_memo: thick trunk and trunk shell shapes are built once per radius and offset");
            }
        }
        return s;
    }
}
