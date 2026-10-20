package bons.furious.patch.farmersdelight_basket;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.slf4j.Logger;
import vectorwing.farmersdelight.common.block.entity.Basket;

/**
 * Bons and Furious switch farmersdelight_basket_query_direct (since 1.0.45; Farmer's Delight 1.20.1-1.3.4; server side).
 *
 * What Farmer's Delight does. Every enabled basket that is not full and not on its transfer cooldown runs
 * BasketBlockEntity.pullItems every tick (the cooldown is only set after a successful pickup, so an idle basket searches
 * every tick), which starts with getCaptureItems(level, basket, facing): it takes the facing's collection shape (Basket's
 * default: one of six constant Block.box shapes), converts it back into a new list of boxes (toAabbs), streams it,
 * flat-maps every box (moved to the basket: getLevelX/Y/Z - 0.5) to level.getEntitiesOfClass(ItemEntity, box,
 * ENTITY_STILL_ALIVE) and collects the result into a new list; then pullItems tries to capture each item in turn.
 *
 * What the switch does. getCaptureItems asks the basket for the same shape and makes the same getEntitiesOfClass call per
 * box, in the same order and with the same arguments (getLevelX, getLevelY, getLevelZ called per box in that order, before
 * the query), all queries before any capture as before; the boxes of one shape object are remembered for vanilla's
 * immutable shape classes (CubeVoxelShape, ArrayVoxelShape, by exact class; any other shape class converts every time), and
 * no stream, flat map or collector is built. With one box (every Farmer's Delight shape) the query's own new ArrayList is
 * the result (the same elements in the same order, a fresh ArrayList as before); with several, their concatenation in a new
 * ArrayList. Identical result for every caller.
 *
 * -Dbons_and_furious.farmersdelightBasketQueryDirect=false runs the original. Farmer's Delight is MIT; this class carries
 * only our own logic.
 */
public final class BasketQuery {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.farmersdelightBasketQueryDirect", "true"));
    private static volatile boolean announced;

    /** One remembered shape and its boxes (the boxes are immutable AABBs; the array is never handed out). */
    private record Entry(VoxelShape shape, AABB[] boxes) {}

    /** A few slots by facing index; each holds the last shape object seen there. Final fields make an entry safe to share. */
    private static final Entry[] SLOTS = new Entry[8];

    private static final Class<?> CUBE = shapeClass("net.minecraft.world.phys.shapes.CubeVoxelShape");
    private static final Class<?> ARRAY = shapeClass("net.minecraft.world.phys.shapes.ArrayVoxelShape");

    private BasketQuery() {
    }

    private static Class<?> shapeClass(String name) {
        try {
            return Class.forName(name, false, VoxelShape.class.getClassLoader());
        } catch (Throwable t) {
            return null;
        }
    }

    /** BasketBlockEntity.getCaptureItems(level, basket, facingIndex), wrapped. */
    public static List<ItemEntity> captureItems(Level level, Basket basket, int facingIndex, Operation<List<ItemEntity>> original) {
        if (!enabled) return original.call(level, basket, facingIndex);
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: farmersdelight_basket_query_direct applies (baskets look for items with one query per box, without streams)");
        }
        AABB[] boxes = boxes(basket.getFacingCollectionArea(facingIndex), facingIndex);
        if (boxes.length == 1) return query(level, basket, boxes[0]);
        List<ItemEntity> out = new ArrayList<>();
        for (AABB box : boxes) out.addAll(query(level, basket, box));
        return out;
    }

    private static List<ItemEntity> query(Level level, Basket basket, AABB box) {
        List<ItemEntity> found = level.m_6443_(ItemEntity.class, box.m_82386_(basket.getLevelX() - 0.5, basket.getLevelY() - 0.5, basket.getLevelZ() - 0.5),
                EntitySelector.f_20402_);
        return found.getClass() == ArrayList.class ? found : new ArrayList<>(found);
    }

    /** shape.toAabbs() as an array; remembered per shape object for vanilla's immutable shape classes. */
    static AABB[] boxes(VoxelShape shape, int facingIndex) {
        Class<?> c = shape.getClass();
        boolean immutable = c == CUBE || c == ARRAY;
        int slot = facingIndex & 7;
        if (immutable) {
            Entry e = SLOTS[slot];
            if (e != null && e.shape() == shape) return e.boxes();
        }
        AABB[] boxes = shape.m_83299_().toArray(new AABB[0]);
        if (immutable) SLOTS[slot] = new Entry(shape, boxes);
        return boxes;
    }
}
