package bons.furious.patch.curios_presence;

import net.minecraft.world.entity.LivingEntity;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import top.theillusivec4.curios.api.CuriosApi;

/**
 * Bons and Furious switch curios_render_layer_empty_skip (Curios API 5.14.1+1.20.1, LGPL-3.0; client; since 1.0.49). Idea:
 * Collections Of Optimizations (curios.skipNonPlayerRenderLayer), idea text only; COO removes Curios' render layer from
 * every non-player renderer (mobs that do have curio slots then lose their curios); ours keeps the layer and skips only
 * what renders nothing.
 *
 * Curios adds its CuriosLayer to every living entity renderer, so every visible mob pays, every frame: a PoseStack push,
 * CuriosApi.getCuriosInventory(entity).ifPresent(lambda capturing eleven values), a pop. For an entity without a Curios
 * inventory (every type no datapack gives slots to) the inventory is empty, the lambda never runs and the push and pop
 * cancel out. CuriosLayerEmptySkipMixin asks the same question first and returns at once when there is no inventory; an
 * entity with an inventory renders exactly as shipped: when curios_inventory_lookup_layer's record already holds its
 * inventory handle the shipped code runs after that one field read, otherwise after one more inventory lookup. Only while
 * curios_inventory_lookup_layer is on, so that lookup is cheap. The skip itself is decided only by the full
 * CuriosApi.getCuriosInventory answer (whatever wraps it), never by the record.
 * Mods that hook CuriosLayer inside the per-slot lambda (Vivecraft) are untouched: that lambda never runs for an entity
 * without an inventory. -Dbons_and_furious.curiosRenderLayerEmptySkip=false renders every entity as shipped.
 */
public final class CuriosRenderSkip {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.curiosRenderLayerEmptySkip", "true"));
    private static volatile boolean announced;

    private CuriosRenderSkip() {
    }

    /** True when CuriosLayer.render would draw nothing for this entity: it has no Curios inventory. */
    public static boolean skip(LivingEntity entity) {
        if (!enabled || entity == null || !CuriosInventoryLookup.enabled || !CuriosInventoryLookup.READY) return false;
        if (CuriosInventoryLookup.keptInventory(entity)) return false;   // a slotted entity: the shipped render, no lookup first
        if (CuriosApi.getCuriosInventory(entity).isPresent()) return false;
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: curios_render_layer_empty_skip applies (Curios' render layer skips entities without curio slots)");
        }
        return true;
    }
}
