package bons.furious.patch.itemfilters;

import dev.latvian.mods.itemfilters.item.StringValueData;
import dev.latvian.mods.itemfilters.item.TagFilterItem;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch itemfilters_tag_key_reuse (Item Filters 2001.1.0-build.53 for Minecraft 1.20.1, both sides; our
 * pack does not install Item Filters, the switch is for other packs, proven offline on the real classes).
 *
 * <p>TagFilterItem.filter(filter, stack) answers {@code stack.isEmpty() ? false : stack.is(TagKey.create(ITEM, new
 * ResourceLocation(getValue(filter))))} and filterItem(filter, item) the same on {@code item.builtInRegistryHolder()}.
 * getValue(filter) reads the filter stack's cached TagData (Item Filters' ItemStack mixin keeps one per stack), whose
 * getValue() already holds the parsed TagKey, and turns that key back into the text "namespace:path" on every call; the
 * filter then parses the text again, validates it, and asks the global TagKey interner for the key it started from. FTB
 * Quests runs this for every item task against the inventory, storage and sorting mods for every item they move.
 *
 * <p>With the switch on, the two methods read the TagKey the data object holds and test the stack (or holder) with it. The
 * answer is identical because, when this happens:
 * <ul>
 * <li>the receiver is exactly TagFilterItem and the data exactly TagFilterItem.TagData (no subclass can change getValue,
 *   getStringValueData or the text form), and the value is a TagKey of the item registry whose location is a plain
 *   ResourceLocation made only of vanilla path and namespace characters;</li>
 * <li>that key is the interner's own instance: TagKey.create(ITEM, its location) returned it (checked once per stored value
 *   and remembered on the data object, VerifiedKeyHolder). TagData.fromString builds its value with TagKey.create, and the
 *   data object keeps it alive, so the interner keeps answering with it;</li>
 * <li>the text form of such a location parses back to an equal location (toString is namespace + ':' + path, the string
 *   constructor splits at the first ':', and neither part can contain ':'), its characters pass Minecraft's validity rule
 *   (the hooks known in packs only widen that rule, Oculus' one extra refusal is the upper-case path DUMMY), so stock's
 *   TagKey.create returns this same instance and stock's stack.is / holder.is gets the same object we pass;</li>
 * <li>the calls before the test are the ones stock makes, in stock's order: the empty-stack and AIR checks first (stock
 *   then answers false without reading the filter), item.builtInRegistryHolder() before the filter for filterItem, then
 *   getStringValueData(filter) and the data's getValue(), which runs the same lazy NBT load (and throws the same
 *   ResourceLocationException for a bad stored tag) that stock's getValue(filter) runs first. Null arguments and every other
 *   case go to the unchanged method, which repeats those two reads (pure once loaded) and answers as before.</li>
 * </ul>
 * What the switch no longer does: the per-call String building, ResourceLocation parsing and validation and the interner
 * lookup, all without effects (the interner already holds the key).
 *
 * <p>-Dbons_and_furious.itemfiltersTagKeyReuse=false turns it off at runtime. SHADOW MODE for rigs:
 * -Dbons_and_furious.itemfiltersTagKeyReuse.shadow=true also runs stock's expression on every answered call, returns stock's
 * answer and counts SHADOW_CHECKS / SHADOW_MISMATCHES (a different answer or a different key object; WARN for the first 20).
 */
public final class TagKeyReuse {
    /** Runtime switch (the config switch acts when classes are transformed). */
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.itemfiltersTagKeyReuse", "true"));
    /** Shadow mode (see the class comment). */
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.itemfiltersTagKeyReuse.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    /** Calls answered with the kept key (statistics for probes; plain field, racy by design). */
    public static long answered;
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    private static volatile boolean announced;

    private TagKeyReuse() {
    }

    /**
     * The TagKey the filter stack's tag filter data holds, when it can stand for the key stock re-parses; null when the
     * unchanged method must run. May throw exactly what stock's getValue(filter) throws first (the lazy NBT load).
     */
    @SuppressWarnings("unchecked")
    public static TagKey<Item> keptKey(Object self, ItemStack filter) {
        if (self.getClass() != TagFilterItem.class) return null;
        StringValueData<?> data = ((TagFilterItem) self).getStringValueData(filter);
        if (data == null || data.getClass() != TagFilterItem.TagData.class) return null;
        Object value = data.getValue();
        if (!(value instanceof TagKey<?> key)) return null;          // null: stock builds minecraft: from ""
        VerifiedKeyHolder holder = (VerifiedKeyHolder) data;
        if (holder.bons$verifiedTagKey() == key) return (TagKey<Item>) key;
        if (key.f_203867_() != Registries.f_256913_) return null;     // stock's key is an item tag key
        ResourceLocation location = key.f_203868_();
        if (location == null || location.getClass() != ResourceLocation.class
                || !vanillaNamespace(location.m_135827_()) || !vanillaPath(location.m_135815_())) return null;
        if (TagKey.m_203882_(Registries.f_256913_, location) != key) return null;   // not the interned instance
        holder.bons$setVerifiedTagKey(key);
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: itemfilters_tag_key_reuse: Item Filters' tag filters test with the TagKey they already hold{}",
                    SHADOW ? " (SHADOW MODE: stock's parse runs every time and is compared)" : "");
        }
        return (TagKey<Item>) key;
    }

    /**
     * TagFilterItem.filter: 0 = run Item Filters' body, 1 = answer true, 2 = answer false. Same reads as stock, in stock's
     * order: the stack's emptiness first (empty: stock answers false without reading the filter), then the filter's data.
     */
    public static int filter(Object self, ItemStack filter, ItemStack stack) {
        if (!enabled || filter == null || stack == null || stack.m_41619_()) return 0;
        TagKey<Item> key = keptKey(self, filter);
        if (key == null) return 0;
        boolean ours = stack.m_204117_(key);
        if (!SHADOW) {
            answered++;
            return ours ? 1 : 2;
        }
        TagKey<Item> stock = TagKey.m_203882_(Registries.f_256913_, new ResourceLocation(((TagFilterItem) self).getValue(filter)));
        boolean theirs = stack.m_204117_(stock);
        shadow(theirs, ours, stock, key, stack);
        return theirs ? 1 : 2;
    }

    /**
     * TagFilterItem.filterItem: 0 = run Item Filters' body, 1 = answer true, 2 = answer false. Stock compares with AIR
     * (answers false without reading the filter), then reads item.builtInRegistryHolder(), then the filter: same here.
     */
    public static int filterItem(Object self, ItemStack filter, Item item) {
        if (!enabled || filter == null || item == null || item == net.minecraft.world.item.Items.f_41852_) return 0;
        Holder.Reference<Item> holder = item.m_204114_();
        TagKey<Item> key = keptKey(self, filter);
        if (key == null) return 0;
        boolean ours = holder.m_203656_(key);
        if (!SHADOW) {
            answered++;
            return ours ? 1 : 2;
        }
        TagKey<Item> stock = TagKey.m_203882_(Registries.f_256913_, new ResourceLocation(((TagFilterItem) self).getValue(filter)));
        boolean theirs = holder.m_203656_(stock);
        shadow(theirs, ours, stock, key, holder);
        return theirs ? 1 : 2;
    }

    private static void shadow(boolean theirs, boolean ours, TagKey<Item> stock, TagKey<Item> key, Object what) {
        SHADOW_CHECKS.incrementAndGet();
        if (theirs != ours || stock != key) {
            long n = SHADOW_MISMATCHES.incrementAndGet();
            if (n <= 20) {
                LOGGER.warn("Bons and Furious: itemfilters_tag_key_reuse SHADOW MISMATCH {}: {} with {}: stock {} (key {}), kept key {} ({}{})",
                        n, what, key, theirs, System.identityHashCode(stock), ours, System.identityHashCode(key), stock == key ? "" : ", other key object");
            }
        }
    }

    /** Minecraft's namespace characters: a-z 0-9 _ - . */
    static boolean vanillaNamespace(String s) {
        if (s == null) return false;
        for (int i = 0, n = s.length(); i < n; i++) {
            char c = s.charAt(i);
            if (!(c == '_' || c == '-' || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '.')) return false;
        }
        return true;
    }

    /** Minecraft's path characters: a-z 0-9 _ - . / */
    static boolean vanillaPath(String s) {
        if (s == null) return false;
        for (int i = 0, n = s.length(); i < n; i++) {
            char c = s.charAt(i);
            if (!(c == '_' || c == '-' || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '/' || c == '.')) return false;
        }
        return true;
    }
}
