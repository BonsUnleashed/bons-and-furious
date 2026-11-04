package bons.furious.patch.immersive_overlays;

import com.mojang.logging.LogUtils;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

/**
 * Bons and Furious switch immersivearmors_steampunk_item_identity (Immersive Armors 1.6.1 for Minecraft 1.20.1 Forge; client).
 * SRG member names.
 *
 * What Immersive Armors does. Its hotbar overlay hook (OverlayRenderer.renderOverlay, every frame from the hotbar overlay's
 * RenderGuiOverlayEvent.Post) walks the local player's four armour slots and, for each, asks the item registry for the
 * slot item's id (BuiltInRegistries.ITEM.getKey: a reverse lookup) and compares it with a freshly built
 * ResourceLocation for "immersive_armors:steampunk_chestplate" (Main.locate: a new, validated ResourceLocation); a match
 * draws the steampunk clock and compass once for that slot. A player wearing none of the mod's armour still pays four
 * reverse lookups and four ResourceLocations per frame.
 *
 * What the switch does. The steampunk chestplate Item is resolved once from the same registry (containsKey, get, and the
 * reverse check getKey(item) == that id, so an alias or a missing item can never pass); then a slot matches when its item
 * IS that Item. In one registry a key names exactly one object and that object has exactly that key (unregistered items
 * report the default key minecraft:air), so "getKey(item).equals(id)" and "item == get(id)" are the same test for every
 * item; the slots are walked in the same order and renderSteampunkHud runs for exactly the same slots. When the chestplate
 * cannot be resolved (the mod registered no such item) the stock loop runs every frame, unchanged.
 *
 * -Dbons_and_furious.immersivearmorsSteampunkItemIdentity=false switches it off at run time.
 * -Dbons_and_furious.immersivearmorsSteampunkItemIdentity.shadow=true (verification runs only): every slot is also tested
 * the stock way and SHADOW_MISMATCHES counts slots where the two tests disagree (WARN for the first 20).
 */
public final class SteampunkOverlay {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.immersivearmorsSteampunkItemIdentity", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.immersivearmorsSteampunkItemIdentity.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    /** The id the stock test compares with (Main.locate("steampunk_chestplate")), set by the mixin on the first frame. */
    private static volatile ResourceLocation id;
    /** The resolved chestplate; null until resolved, or when it cannot be (UNRESOLVED below). */
    private static volatile Item chestplate;
    private static volatile boolean unresolved;

    private SteampunkOverlay() {
    }

    /** The chestplate once resolved, else null (then ask gaveUp(), or resolve with chestplate(id)). */
    public static Item resolved() {
        return chestplate;
    }

    /** True once resolution failed: the stock loop runs from then on. */
    public static boolean gaveUp() {
        return unresolved;
    }

    /**
     * The steampunk chestplate Item for the given id (Main.locate("steampunk_chestplate"), passed in by the mixin), or null
     * when the stock loop must run: the item is not registered, or the registry maps it back to another key.
     */
    public static Item chestplate(ResourceLocation stockId) {
        Item s = chestplate;
        if (s != null) return s;
        if (unresolved) return null;
        Registry<Item> items = BuiltInRegistries.f_257033_;
        Item found = items.m_7804_(stockId) ? items.m_7745_(stockId) : null;
        if (found == null || !stockId.equals(items.m_7981_(found))) {
            unresolved = true;
            LOGGER.info("Bons and Furious: immersivearmors_steampunk_item_identity stands aside: {} is not a registered item; Immersive Armors' overlay loop is left as shipped", stockId);
            return null;
        }
        id = stockId;
        chestplate = found;
        LOGGER.info("Bons and Furious: immersivearmors_steampunk_item_identity applies (the steampunk overlay compares armour items with {} instead of looking every slot up in the registry){}",
                stockId, SHADOW ? " - shadow verification on" : "");
        return found;
    }

    /** True when the armour slot item is the steampunk chestplate (the stock id test, answered by identity). */
    public static boolean matches(ItemStack slot, Item chestplate) {
        Item item = slot.m_41720_();
        boolean ours = item == chestplate;
        if (SHADOW) {
            SHADOW_CHECKS.incrementAndGet();
            boolean stock = BuiltInRegistries.f_257033_.m_7981_(item).equals(id);
            if (stock != ours) {
                long m = SHADOW_MISMATCHES.incrementAndGet();
                if (m <= 20) LOGGER.warn("Bons and Furious: immersivearmors_steampunk_item_identity shadow mismatch #{}: {} (stock {}, identity {})", m, item, stock, ours);
                return stock;
            }
        }
        return ours;
    }
}
