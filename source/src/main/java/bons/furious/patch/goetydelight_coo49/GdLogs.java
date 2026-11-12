package bons.furious.patch.goetydelight_coo49;

import bons.furious.mixin.goetydelight_coo49.GdNamedContentsAccessor;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import net.neoforged.neoforge.registries.IdMappingEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious 1.0.49, group goetydelight_coo49 (Goety's Delight 1.5.0-1.21.1-20260911_alpha-7 for NeoForge 1.21.1).
 * Both sides. Helper of the group's mixins; no code of Goety's Delight is carried (its code is LGPL-2.1, ours is our own).
 *
 * <p>Goety's Delight builds the list of every log item for its boat plate recipe by streaming every registered item,
 * building an ItemStack for each ({@code item.getDefaultInstance()}) and asking {@code stack.is(ItemTags.LOGS)}:
 * RecraftBoatPlate.getLogItems (from assemble and getResultItem: crafting, the recipe book's indexing and recipe viewers)
 * and CraftingDisplayHandler.getLogItems (client, every 100 ticks while a crafting table with a boat plate is open).
 *
 * <p>goetydelight_log_items_memo: the list is kept per method and handed out again while nothing it depends on changed.
 * It is a pure function of the item registry's iteration order and the items' log tag: the order changes only when
 * NeoForge loads or reverts an id snapshot (followed by IdMappingEvent), the tag binding only through
 * MappedRegistry.bindTags / resetTags, which give the minecraft:logs tag object a new contents list (checked by identity on
 * every call) and are followed by TagsUpdatedEvent. Both events bump a counter (listeners at HIGHEST priority, registered
 * on first use), so a kept list is used only while the counters and the tag's contents list are the ones it was built
 * under (read BEFORE it was built). The callers (assemble, getResultItem, updateCraftingDisplay; private static methods of
 * their own classes) only read the list (isEmpty, size, get), so handing the same list out again is not observable.
 * Residual, documented: a call on another thread in the microseconds between a reload's binding and its event sees the
 * list from before the reload, where the stock scan would read a half-bound tag set too.
 *
 * <p>goetydelight_log_scan_lean: the scan's test asks the item's own registry holder for the tag instead of building an
 * ItemStack first. Exact for every item class that inherits Item's getDefaultInstance (= new ItemStack(this)),
 * builtInRegistryHolder, asItem, components and verifyComponentsAfterLoad (the only code the 1.21.1 stack constructor runs
 * for such an item, besides building the throwaway stack's own component map): that stack's getItem() is the item (AIR for
 * AIR, count 1) and ItemStack.is(TagKey) asks exactly that holder. Any class overriding one of those runs the stock test.
 *
 * <p>Runtime flags: bons_and_furious.goetydelightLogItemsMemo, bons_and_furious.goetydelightLogScanLean (=false: off).
 * SHADOW MODE: -Dbons_and_furious.goetydelightLogItemsMemo.shadow=true runs the stock scan on every kept hand-out and
 * compares it (same items, same order): SHADOW_CHECKS / SHADOW_MISMATCHES, WARN for the first 20, and hands out the fresh
 * list.
 */
public final class GdLogs {
    public static final int RECRAFT = 0, DISPLAY = 1;
    static final String MEMO_KEY = "goetydelight_log_items_memo", LEAN_KEY = "goetydelight_log_scan_lean";

    public static volatile boolean memo = flag("goetydelightLogItemsMemo");
    public static volatile boolean lean = flag("goetydelightLogScanLean");
    /** Shadow mode (see the class comment). */
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.goetydelightLogItemsMemo.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    /** Statistics (plain fields, racy by design): lists handed out again, scans made, lean tests answered from the holder. */
    public static long keptHits, scans, leanTests;

    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    /** Bumped by the listeners (TagsUpdatedEvent, IdMappingEvent). */
    static volatile int tagGeneration, idGeneration;
    private static volatile boolean announcedMemo, announcedLean;
    /** One kept list per method (RECRAFT, DISPLAY). */
    private static volatile Kept recraft, display;

    private record Kept(List<?> list, Object contents, int tagGen, int idGen) {}

    private GdLogs() {
    }

    private static boolean flag(String name) {
        return !"false".equalsIgnoreCase(System.getProperty("bons_and_furious." + name, "true"));
    }

    /** The two listeners, registered when the memo is first used; READY false = the memo stays off (stock scans). */
    private static final class Listeners {
        static final boolean READY = register();

        private static boolean register() {
            try {
                NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, TagsUpdatedEvent.class, e -> tagGeneration++);
                NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, IdMappingEvent.class, e -> idGeneration++);
                return true;
            } catch (Throwable t) {
                LOGGER.warn("Bons and Furious: {} cannot listen for tag reloads and id remaps ({}); Goety's Delight scans the item registry as before", MEMO_KEY, t.toString());
                return false;
            }
        }
    }

    /** The minecraft:logs tag object of the item registry, found once (MappedRegistry keeps it across reloads). */
    private static volatile HolderSet.Named<Item> logsTag;

    private static Object logsContents() {
        HolderSet.Named<Item> named = logsTag;
        if (named == null) {
            Optional<HolderSet.Named<Item>> found = BuiltInRegistries.ITEM.getTag(ItemTags.LOGS);
            if (found.isEmpty()) return null;            // not bound yet (main menu): nothing is kept
            logsTag = named = found.get();
        }
        return ((GdNamedContentsAccessor) (Object) named).bons$goetydelightLogContents();
    }

    /** Wrapper of getLogItems() in RecraftBoatPlate ({@code site} RECRAFT) and CraftingDisplayHandler (DISPLAY). */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static List logs(int site, Operation<List> original) {
        if (!memo || !Listeners.READY) return original.call();
        int tg = tagGeneration, ig = idGeneration;       // read before anything the list is built from
        Object contents = logsContents();
        Kept k = site == RECRAFT ? recraft : display;
        if (contents != null && k != null && k.contents == contents && k.tagGen == tg && k.idGen == ig) {
            if (SHADOW) return shadow(site, k.list, original.call());
            keptHits++;
            return k.list;
        }
        List fresh = original.call();
        scans++;
        if (contents != null) {
            Kept nk = new Kept(fresh, contents, tg, ig);
            if (site == RECRAFT) recraft = nk;
            else display = nk;
            if (!announcedMemo) {
                announcedMemo = true;
                LOGGER.info("Bons and Furious: {}: Goety's Delight keeps its log item list until tags reload or item ids remap{}", MEMO_KEY,
                        SHADOW ? " (SHADOW MODE: every hand-out is compared with a fresh scan)" : "");
            }
        }
        return fresh;
    }

    @SuppressWarnings("rawtypes")
    private static List shadow(int site, List kept, List fresh) {
        SHADOW_CHECKS.incrementAndGet();
        boolean same = kept.size() == fresh.size();
        for (int i = 0; same && i < kept.size(); i++) same = kept.get(i) == fresh.get(i);
        if (!same && SHADOW_MISMATCHES.incrementAndGet() <= 20) {
            LOGGER.warn("Bons and Furious: {} SHADOW MISMATCH {} ({}): kept {} logs, a fresh scan found {}", MEMO_KEY, SHADOW_MISMATCHES.get(),
                    site == RECRAFT ? "RecraftBoatPlate" : "CraftingDisplayHandler", kept.size(), fresh.size());
        }
        return fresh;
    }

    /** Item classes whose log test may skip the throwaway ItemStack (see the class comment). */
    private static final ClassValue<Boolean> PLAIN = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> c) {
            try {
                return inheritedFromItem(c, "getDefaultInstance", MethodType.methodType(ItemStack.class))
                        && inheritedFromItem(c, "builtInRegistryHolder", MethodType.methodType(Holder.Reference.class))
                        && inheritedFromItem(c, "asItem", MethodType.methodType(Item.class))
                        && inheritedFromItem(c, "components", MethodType.methodType(DataComponentMap.class))
                        && inheritedFromItem(c, "verifyComponentsAfterLoad", MethodType.methodType(void.class, ItemStack.class));
            } catch (Throwable t) {
                return false;
            }
        }
    };

    /**
     * The method {@code name} of {@code type} that {@code c} resolves to is Item's own (or IItemExtension's default). Since 1.0.49.2
     * the JVM's own resolution of that one method (privateLookupIn + findVirtual + revealDirect), not Class.getMethod: getMethod
     * resolves the parameter and return types of every public method, and on a dedicated server item classes whose client-side
     * methods name client-only classes made the loader log an "invalid dist" ERROR for each such class (31 at start in the
     * all-switches-on qualification server). False when the method cannot be asked this way (the stock test runs).
     */
    private static boolean inheritedFromItem(Class<?> c, String name, MethodType type) {
        for (Class<?> anchor : new Class<?>[] {c, Item.class}) {
            try {
                MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(anchor, MethodHandles.lookup());
                Class<?> d = lookup.revealDirect(lookup.findVirtual(c, name, type)).getDeclaringClass();
                return d == Item.class || d.getName().equals("net.neoforged.neoforge.common.extensions.IItemExtension");
            } catch (Throwable t) {
                // not this way: the next anchor, then false (the stock test)
            }
        }
        return false;
    }

    /**
     * The last item class PLAIN answered false / true for (plain fields: a race only repeats the ClassValue lookup; each
     * field only ever holds a class of its own answer). An item that stands aside would otherwise pay the ClassValue
     * lookup before the original test; a registry lists a mod's items together, so consecutive items mostly share a class.
     */
    private static Class<?> lastAside, lastPlain;

    private static boolean plain(Class<?> c) {
        if (c == lastPlain) return true;
        if (c == lastAside) return false;
        if (PLAIN.get(c)) {
            lastPlain = c;
            return true;
        }
        lastAside = c;
        return false;
    }

    /** Wrapper of the scan's test lambda$getLogItems$0(Item) in both classes. */
    public static boolean isLog(Item item, Operation<Boolean> original) {
        if (!lean || item == null || !plain(item.getClass())) return original.call(item);
        if (!announcedLean) {
            announcedLean = true;
            LOGGER.info("Bons and Furious: {}: Goety's Delight's log scan asks each item's tags directly (no throwaway ItemStack per item)", LEAN_KEY);
        }
        leanTests++;
        return item.builtInRegistryHolder().is(ItemTags.LOGS);
    }
}
