package bons.furious.patch.l2archery_coo49;

import dev.xkmc.l2archery.content.entity.GenericArrowEntity;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch l2archery_arrow_texture_memo (L2 Archery, LGPL-2.1; 1.21.1 tested build 3.0.4; client; exact).
 * None of L2 Archery's code is carried. Ours, idea from Collections Of Optimizations (l2archery.cacheArrowTexture), idea
 * text only.
 *
 * What L2 Archery does: GenericArrowRenderer.getTextureLocation, asked for every rendered L2 arrow on every frame, takes the
 * arrow item of the entity's data (or the starter arrow when that item is no GenericArrowItem), looks its name up in the
 * item registry (BuiltInRegistries.ITEM.getKey) and builds a new location from it (withPath("textures/entity/arrow/
 * arrow.png"), a validated new ResourceLocation).
 *
 * What the switch does: it keeps the answer per arrow item (the item of the entity's data, an Item object) and hands the
 * kept ResourceLocation out again. The answer depends only on that item: the fallback item is fixed, and an item's name
 * in the item registry never changes once the registry is frozen (an unregistered item answers the default key
 * minecraft:air, the same every time). The kept location equals the one the shipped code builds (same namespace, same
 * path; only the object is reused, and everything that uses a texture location compares by value: RenderType.entityCutout
 * keeps its render types by equals). An answer the shipped code did not return (it threw) is never kept. The memo holds
 * one entry per arrow item type (a few dozen at most).
 *
 * Flag: -Dbons_and_furious.l2archeryArrowTextureMemo=false builds the location every time. HITS counts kept answers only
 * with -Dbons_and_furious.l2archeryArrowTextureMemo.count=true (the harness; nothing is counted in game).
 */
public final class ArrowTextures {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.l2archeryArrowTextureMemo", "true"));
    static final boolean COUNT = Boolean.getBoolean("bons_and_furious.l2archeryArrowTextureMemo.count");
    /** Answers handed out from the memo (counted only with the count property: for the harness). */
    public static final AtomicLong HITS = new AtomicLong();
    private static final ConcurrentHashMap<Item, ResourceLocation> KEPT = new ConcurrentHashMap<>();
    private static volatile boolean announced;

    private ArrowTextures() {
    }

    /** The arrow item the shipped code starts from (the entity data's arrow item). */
    private static Item item(GenericArrowEntity entity) {
        return entity.data.arrow().item();
    }

    /** HEAD of getTextureLocation: the kept location of this arrow item, or null to run the shipped code. */
    public static ResourceLocation probe(GenericArrowEntity entity) {
        if (!enabled) return null;
        Item item = item(entity);
        if (item == null) return null;
        ResourceLocation kept = KEPT.get(item);
        if (COUNT && kept != null) HITS.incrementAndGet();
        return kept;
    }

    /** RETURN of getTextureLocation (the shipped code built the location): keep it for this arrow item. */
    public static ResourceLocation fill(GenericArrowEntity entity, ResourceLocation result) {
        if (!enabled || result == null) return result;
        Item item = item(entity);
        if (item != null && KEPT.putIfAbsent(item, result) == null && !announced) {
            announced = true;
            LOGGER.info("Bons and Furious: l2archery_arrow_texture_memo applies (L2 Archery arrows keep their texture location per arrow item)");
        }
        return result;
    }
}
