package bons.furious.patch.cookingforblockheads_c2;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import net.blay09.mods.cookingforblockheads.api.ToasterHandler;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

/**
 * Bons and Furious switch cookingforblockheads_compat_reload_once (Cooking for Blockheads 16.0.15 for 1.20.1; both sides:
 * the server's data reload, which in singleplayer shares these collections with the client). A fix; no Cooking for
 * Blockheads code is carried here.
 *
 * Cooking for Blockheads loads its data/.../cookingforblockheads/compat/*.json files with a server reload listener
 * (JsonCompatLoader.onResourceManagerReload), so they load again at every server start (every singleplayer world joined in
 * one game session) and every /reload, and every load appends: the non-food recipe list, the tool, water and milk lists, the
 * kitchen connector blocks, and new ItemStack keys in the oven fuel, oven recipe and toaster maps (ItemStack has identity
 * equality, so a reload adds a second key instead of replacing). Readers scan these linearly; the sink offers each water item
 * once per load; and after editing a json and /reload, old and new map entries coexist and identity-hash order picks the
 * answer.
 *
 * What changes, only while a json load runs (on the thread running it): an entry that repeats one an EARLIER json load added
 * (same item, count and NBT; the same block for connectors), counted per entry so duplicates within one load are kept as the
 * first load had them, is not added again; for the three maps the earlier key keeps its place and takes the new value
 * unless the value is equal (then the earlier value stays too). Registrations through Cooking for Blockheads' API (its own
 * defaults, IMC, other mods) are never touched. Result: the first load is exactly the original (nothing earlier to repeat);
 * with unchanged data every later load leaves every collection exactly as the first load left it (same objects, same order,
 * same map entries; a json toaster entry is compared by its output); with changed data the current json's values win and
 * new entries are added as before. Entries a json no longer lists stay, as in the original.
 */
public final class CompatReloadOnce {
    /** Runtime switch. -Dbons_and_furious.cookingforblockheadsCompatReloadOnce=false turns it off. */
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.cookingforblockheadsCompatReloadOnce", "true"));
    /** Adds skipped and map values replaced in place (tests and the log). */
    public static final AtomicLong SKIPPED = new AtomicLong(), REPLACED = new AtomicLong();
    private static final Logger LOGGER = LogUtils.getLogger();
    private static volatile boolean announced;

    /** The thread running a json load, or null. */
    private static volatile Thread loader;
    private static int loads;

    /** Per collection: the entries earlier json loads added, per key in order, and how often this load has asked so far. */
    private static final class Book {
        final Map<Object, List<Object>> added = new HashMap<>();
        final Map<Object, Integer> seen = new HashMap<>();
    }

    private static final Map<String, Book> BOOKS = new HashMap<>();

    private CompatReloadOnce() {
    }

    /** Around JsonCompatLoader.onResourceManagerReload: one json load (all compat files). */
    public static void load(Operation<Void> original, Object resourceManager) {
        if (!enabled || loader != null) {
            original.call(resourceManager);
            return;
        }
        synchronized (BOOKS) {
            for (Book b : BOOKS.values()) b.seen.clear();
        }
        loader = Thread.currentThread();
        try {
            original.call(resourceManager);
        } finally {
            loader = null;
            loads++;
        }
    }

    static boolean active() {
        return loader == Thread.currentThread();
    }

    /** Key of a json-made stack: item, count and NBT (json stacks are new ItemStack(item): count 1, no NBT). */
    record StackKey(Item item, int count, CompoundTag tag) {
        static StackKey of(ItemStack s) {
            return new StackKey(s.m_41720_(), s.m_41613_(), s.m_41783_());
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof StackKey k && k.item == item && k.count == count && Objects.equals(k.tag, tag);
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(item) * 31 + count;
        }
    }

    private static Object keyOf(Object entry) {
        return entry instanceof ItemStack s ? StackKey.of(s) : entry;
    }

    /** The entry an earlier json load added for this occurrence of the key, or null (and records the new one). */
    private static Object earlier(String collection, Object entry, java.util.function.Predicate<Object> stillThere) {
        synchronized (BOOKS) {
            Book b = BOOKS.computeIfAbsent(collection, k -> new Book());
            Object key = keyOf(entry);
            int occ = b.seen.merge(key, 1, Integer::sum);
            List<Object> prev = b.added.computeIfAbsent(key, k -> new ArrayList<>(1));
            if (occ <= prev.size()) {
                Object old = prev.get(occ - 1);
                if (stillThere.test(old)) {
                    return old;
                }
                prev.set(occ - 1, entry);
                return null;
            }
            prev.add(entry);
            return null;
        }
    }

    private static boolean containsIdentity(Collection<?> c, Object o) {
        for (Object x : c) {
            if (x == o) return true;
        }
        return false;
    }

    /** A list add (tools, water, milk, connector blocks): skipped when it repeats an earlier json load's entry. */
    public static boolean listAdd(String collection, Collection<Object> list, Object entry, Operation<Boolean> original) {
        if (active() && earlier(collection, entry, old -> containsIdentity(list, old)) != null) {
            skipped(collection);
            return true;          // what List.add would have answered
        }
        return original.call(list, entry);
    }

    /** The json non-food list (filled through findItemStack(...).ifPresent(nonFoodRecipes::add)): empty when it repeats. */
    public static Optional<ItemStack> nonFood(Collection<ItemStack> list, Optional<ItemStack> found) {
        if (found.isPresent() && active() && earlier("nonFood", found.get(), old -> containsIdentity(list, old)) != null) {
            skipped("nonFood");
            return Optional.empty();
        }
        return found;
    }

    /** A map put (oven fuel, oven recipe, toaster): the earlier json key keeps its place and takes the value (unless equal). */
    public static Object mapPut(String collection, Map<Object, Object> map, Object key, Object value, Operation<Object> original) {
        if (active()) {
            Object old = earlier(collection, key, map::containsKey);
            if (old != null) {
                Object current = map.get(old);
                if (!sameValue(old, current, value)) {
                    REPLACED.incrementAndGet();
                    return original.call(map, old, value);
                }
                skipped(collection);
                return current;   // what put would have answered for an equal value
            }
        }
        return original.call(map, key, value);
    }

    private static boolean sameValue(Object key, Object a, Object b) {
        if (a instanceof ItemStack x && b instanceof ItemStack y) return ItemStack.m_41728_(x, y);
        if (a instanceof Integer x && b instanceof Integer y) return x.intValue() == y.intValue();
        // a json toaster entry is JsonCompatLoader's "stack -> output" (no side effects): equal when the outputs are equal
        if (a instanceof ToasterHandler x && b instanceof ToasterHandler y && key instanceof ItemStack k) {
            ItemStack ox = x.getToasterOutput(k), oy = y.getToasterOutput(k);
            return ox != null && oy != null && ItemStack.m_41728_(ox, oy);
        }
        return false;
    }

    private static void skipped(String collection) {
        SKIPPED.incrementAndGet();
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: cookingforblockheads_compat_reload_once: Cooking for Blockheads' compat files were loaded again (load {}); "
                    + "entries an earlier load already added are not added a second time (first: {})", loads + 1, collection);
        }
    }
}
