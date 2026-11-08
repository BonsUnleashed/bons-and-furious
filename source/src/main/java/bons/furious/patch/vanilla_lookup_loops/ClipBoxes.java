package bons.furious.patch.vanilla_lookup_loops;

import com.mojang.logging.LogUtils;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.ArrayVoxelShape;
import net.minecraft.world.phys.shapes.CubeVoxelShape;
import net.minecraft.world.phys.shapes.SliceShape;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.slf4j.Logger;

/**
 * Bons and Furious switch vanilla_clip_shape_box_memo (Minecraft 1.21.1 on NeoForge 21.1.252, both sides): runtime switch and
 * shadow check of VoxelShapeClipBoxesMixin. No Mojang code here.
 *
 * What vanilla does. VoxelShape.clip tests a ray against one block's shape: every ray step of Level.clip that meets a
 * non-empty shape (line of sight of mobs, projectiles, explosion exposure, block picking) calls it, and for a ray that does
 * not start inside the shape it calls toAabbs, which walks the shape's boxes and builds a new ArrayList holding a new AABB
 * per box, then hands that list to AABB.clip, which only reads it.
 *
 * What the switch does. Each shape of vanilla's own kinds (ArrayVoxelShape, CubeVoxelShape, SliceShape: none overrides
 * toAabbs) keeps an unmodifiable copy of the box list its first clip built (the same AABB objects, in the same order) and
 * hands that copy to AABB.clip from then on. A shape never changes after it is made (its coordinate lists and voxel grid
 * are only written while it is being built; Copycats' setter for a shape's voxel grid is used only by its own
 * ExtensibleVoxelShape, a SliceShape subclass that is never kept, while that shape is built), so the boxes are equal,
 * AABB.clip computes the same hit point, face and position from them, and nothing else read the list. The copy is
 * published through final fields, so a shape shared between threads (block shapes are) is read safely. Other VoxelShape
 * subclasses (other mods' shapes, Lithium's and Radium's specialised shapes) are never kept.
 *
 * -Dbons_and_furious.clipShapeBoxMemo=false switches it off at run time.
 * -Dbons_and_furious.clipShapeBoxMemo.shadow=true (verification runs): a kept list is compared with a fresh toAabbs on
 * every use (SHADOW_CHECKS / SHADOW_MISMATCHES, at most 20 WARN lines) and the fresh list is used.
 */
public final class ClipBoxes {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.clipShapeBoxMemo", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.clipShapeBoxMemo.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    private static volatile boolean announced;

    private ClipBoxes() {
    }

    /** May this shape's box list be kept? Only vanilla's own shape classes, whose toAabbs is VoxelShape's. */
    public static boolean keepable(VoxelShape shape) {
        Class<?> c = shape.getClass();
        return c == ArrayVoxelShape.class || c == CubeVoxelShape.class || c == SliceShape.class;
    }

    /** The copy to keep: unmodifiable, the same AABB objects in the same order. */
    public static List<AABB> keep(List<AABB> fresh) {
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: vanilla_clip_shape_box_memo applies (ray tests reuse each block shape's box list){}",
                    SHADOW ? " - shadow verification on" : "");
        }
        return List.copyOf(fresh);
    }

    /** Shadow mode: the kept list must equal a fresh toAabbs; the fresh list is returned. */
    public static List<AABB> shadow(VoxelShape shape, List<AABB> kept) {
        List<AABB> fresh = shape.toAabbs();
        SHADOW_CHECKS.incrementAndGet();
        if (!fresh.equals(kept)) {
            long m = SHADOW_MISMATCHES.incrementAndGet();
            if (m <= 20) LOGGER.warn("Bons and Furious: vanilla_clip_shape_box_memo shadow mismatch #{}: {} kept {} but now has {}", m, shape, kept, fresh);
        }
        return fresh;
    }
}
