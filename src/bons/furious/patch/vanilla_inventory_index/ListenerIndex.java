package bons.furious.patch.vanilla_inventory_index;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.logging.LogUtils;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ConcurrentModificationException;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.advancements.CriterionTrigger;
import net.minecraft.advancements.critereon.InventoryChangeTrigger;
import net.minecraft.advancements.critereon.ItemPredicate;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.slf4j.Logger;

/**
 * Bons and Furious switch vanilla_inventory_trigger_index (Minecraft 1.20.1 on Forge 47.4.16; server side incl. the
 * integrated server). SRG member names. Idea: InventoryAdvancementAccelerator (a per-player item index of the
 * inventory_changed conditions), idea text only; the exact form (no sampling, no periodic full scans, vanilla order kept)
 * is ours.
 *
 * What vanilla does. Every inventory change of a player (each slot a container or pickup touches) calls
 * InventoryChangeTrigger.trigger, and SimpleCriterionTrigger.trigger (m_66234_) builds a loot context and then walks the
 * player's whole listener HashSet: for each listener it evaluates `instance.matches(inventory, stack, ...) &&
 * instance.player.matches(context)` and grants the matches afterwards, in HashSet order. This pack registers 21,728
 * inventory_changed criteria (recipe advancements included); a player still holds thousands of them.
 *
 * What the switch does. Per player (PlayerAdvancements) the trigger keeps an index of its listener set: the listeners in
 * the set's own iteration order, the positions of "simple" ones under each item of their condition, and the positions of
 * all others. A listener is simple when its instance is exactly InventoryChangeTrigger.TriggerInstance with exactly one
 * item condition of exactly class ItemPredicate, not ItemPredicate.ANY, with no tag and a non-null item set, and that item
 * set is not {shears} (BCLib's HEAD inject answers true for every c:shears item there). When trigger walks the set, the
 * iterator it gets visits only the other listeners and the simple ones listed under the changed stack's item (none for an
 * empty stack), in increasing set position, so in exactly the set's order; vanilla's own loop still evaluates each of
 * them and grants as before.
 *
 * Why the result is identical. For a simple listener vanilla's test is `slots in bounds (pure int checks) &&
 * !stack.isEmpty() && predicate.matches(stack)`, and ItemPredicate.matches tests `this == ANY`, the tag (null), then
 * `items.contains(stack.getItem())` before anything else, so for an empty stack or an item outside the set it answers
 * false with no side effect, and the player predicate is not evaluated (short-circuit): leaving such a listener out
 * changes nothing. Every listener vanilla could match is visited, in the same order, with the same calls, so matches,
 * grants, their order and the loot context's random draws are identical. The index is rebuilt when the set changes:
 * every write of a trigger's listener map goes through addPlayerListener / removePlayerListener /
 * removePlayerListeners (census: no other class names the map field f_66232_), and each bumps the trigger's version. A
 * change during the walk throws ConcurrentModificationException from next(), as the HashSet iterator would. The switch
 * stands down (INFO once) if any other mixin than this switch's own, BCLib's ItemPredicateBuilderMixin (handled above)
 * and Tinkers' Jewelry's MixinItemPredicate (enchantment checks, reached only after the item test) has members merged
 * into ItemPredicate, InventoryChangeTrigger, its TriggerInstance or SimpleCriterionTrigger.
 *
 * -Dbons_and_furious.inventoryTriggerIndex=false switches it off at run time.
 * -Dbons_and_furious.inventoryTriggerIndex.shadow=true (verification runs only): the original full iterator is used, and
 * for every simple listener the index would leave out its item predicate is asked and must answer false
 * (SHADOW_CHECKS / SHADOW_MISMATCHES, WARN for the first 20).
 */
public final class ListenerIndex {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.inventoryTriggerIndex", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.inventoryTriggerIndex.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    /** Test and log support: index builds, indexed walks, listeners left out. */
    public static final AtomicLong BUILDS = new AtomicLong(), WALKS = new AtomicLong(), SKIPPED = new AtomicLong();
    static final String OWN_MIXINS = "bons.furious.mixin.vanilla_inventory_index.";
    static final Set<String> VETTED = Set.of("org.betterx.bclib.mixin.common.shears.ItemPredicateBuilderMixin",
            "dev.ferriarnus.tinkersjewelry.mixin.MixinItemPredicate");
    private static volatile Boolean standDown;
    private static volatile boolean announced;
    private static final ThreadLocal<Context> CURRENT = new ThreadLocal<>();

    /** Implemented by SimpleCriterionTrigger (version of its listener map) and its per-player index cache. */
    public interface Versioned {
        int bons$listenerVersion();

        Map<Object, Index> bons$listenerIndexes();
    }

    /** Implemented by InventoryChangeTrigger.TriggerInstance (accessor). */
    public interface Predicates {
        ItemPredicate[] bons$inventoryIndexPredicates();
    }

    /** Implemented by ItemPredicate (accessors). */
    public interface ItemFields {
        net.minecraft.tags.TagKey<Item> bons$inventoryIndexTag();

        Set<Item> bons$inventoryIndexItems();
    }

    /** The inventory change being triggered on this thread. */
    record Context(Object trigger, Object advancements, ItemStack stack, Context previous) {
    }

    /** One player's listener set of one trigger, indexed. Immutable after construction. */
    public record Index(Set<?> set, int version, Object[] listeners, int[] complex, Map<Item, int[]> byItem) {
    }

    private ListenerIndex() {
    }

    /** InventoryChangeTrigger's private trigger(player, inventory, stack, full, empty, occupied) starts. */
    public static Object enter(Object trigger, Object advancements, ItemStack stack) {
        Context c = new Context(trigger, advancements, stack, CURRENT.get());
        CURRENT.set(c);
        return c;
    }

    public static void exit(Object context) {
        Context c = (Context) context;
        if (c.previous() == null) CURRENT.remove();
        else CURRENT.set(c.previous());
    }

    /** SimpleCriterionTrigger.trigger (m_66234_): its listener-set iterator. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static Iterator iterator(SimpleCriterionTrigger<?> trigger, Set set, Operation<Iterator> original) {
        Context c = CURRENT.get();
        if (!enabled || c == null || c.trigger() != trigger || !(trigger instanceof InventoryChangeTrigger) || standingDown()) {
            return original.call(set);
        }
        Versioned v = (Versioned) trigger;
        Map<Object, Index> cache = v.bons$listenerIndexes();
        Index index;
        synchronized (cache) {
            index = cache.get(c.advancements());
        }
        int version = v.bons$listenerVersion();
        if (index == null || index.set() != set || index.version() != version) {
            index = build(set, version, original);
            synchronized (cache) {
                cache.put(c.advancements(), index);
            }
            BUILDS.incrementAndGet();
        }
        ItemStack stack = c.stack();
        int[] simple = stack.m_41619_() ? null : index.byItem().get(stack.m_41720_());
        if (SHADOW) {
            shadow(index, stack, simple);
            return original.call(set);
        }
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: vanilla_inventory_trigger_index applies (inventory changes test only the advancement conditions that can match the item)");
        }
        WALKS.incrementAndGet();
        SKIPPED.addAndGet(index.listeners().length - index.complex().length - (simple == null ? 0 : simple.length));
        return new Walk(v, version, index.listeners(), index.complex(), simple == null ? EMPTY : simple);
    }

    private static final int[] EMPTY = new int[0];

    @SuppressWarnings({"rawtypes", "unchecked"})
    static Index build(Set set, int version, Operation<Iterator> original) {
        Object[] listeners = new Object[set.size()];
        int n = 0;
        Iterator it = original.call(set);
        while (it.hasNext()) {
            Object l = it.next();
            if (n == listeners.length) listeners = java.util.Arrays.copyOf(listeners, n * 2 + 1);
            listeners[n++] = l;
        }
        if (n != listeners.length) listeners = java.util.Arrays.copyOf(listeners, n);
        int[] complex = new int[n];
        int nc = 0;
        Map<Item, int[]> byItem = new IdentityHashMap<>();
        Map<Item, Integer> counts = new IdentityHashMap<>();
        Set<Item>[] items = new Set[n];
        for (int i = 0; i < n; i++) {
            items[i] = simpleItems(((CriterionTrigger.Listener<?>) listeners[i]).m_13685_());
            if (items[i] == null) complex[nc++] = i;
            else for (Item item : items[i]) counts.merge(item, 1, Integer::sum);
        }
        for (Map.Entry<Item, Integer> e : counts.entrySet()) byItem.put(e.getKey(), new int[e.getValue()]);
        Map<Item, Integer> fill = new IdentityHashMap<>();
        for (int i = 0; i < n; i++) {
            if (items[i] == null) continue;
            for (Item item : items[i]) {
                int k = fill.merge(item, 1, Integer::sum) - 1;
                byItem.get(item)[k] = i;
            }
        }
        return new Index(set, version, listeners, java.util.Arrays.copyOf(complex, nc), byItem);
    }

    /** The item set of a simple listener's single item condition, or null when the listener is not simple. */
    static Set<Item> simpleItems(Object instance) {
        if (instance == null || instance.getClass() != InventoryChangeTrigger.TriggerInstance.class) return null;
        ItemPredicate[] predicates = ((Predicates) instance).bons$inventoryIndexPredicates();
        if (predicates == null || predicates.length != 1) return null;
        ItemPredicate p = predicates[0];
        if (p == null || p.getClass() != ItemPredicate.class || p == ItemPredicate.f_45028_) return null;
        ItemFields f = (ItemFields) p;
        Set<Item> items = f.bons$inventoryIndexItems();
        if (f.bons$inventoryIndexTag() != null || items == null) return null;
        if (items.size() == 1 && items.contains(Items.f_42574_)) return null;   // BCLib: {shears} also matches every c:shears item
        return items;
    }

    /** Walks the complex positions and the stack item's simple positions, merged in increasing set position. */
    static final class Walk implements Iterator<Object> {
        private final Versioned trigger;
        private final int version;
        private final Object[] listeners;
        private final int[] a, b;
        private int i, j;

        Walk(Versioned trigger, int version, Object[] listeners, int[] a, int[] b) {
            this.trigger = trigger;
            this.version = version;
            this.listeners = listeners;
            this.a = a;
            this.b = b;
        }

        @Override
        public boolean hasNext() {
            return this.i < this.a.length || this.j < this.b.length;
        }

        @Override
        public Object next() {
            if (this.trigger.bons$listenerVersion() != this.version) throw new ConcurrentModificationException();
            if (this.i < this.a.length && (this.j >= this.b.length || this.a[this.i] < this.b[this.j])) return this.listeners[this.a[this.i++]];
            if (this.j < this.b.length) return this.listeners[this.b[this.j++]];
            throw new NoSuchElementException();
        }
    }

    private static void shadow(Index index, ItemStack stack, int[] simple) {
        boolean[] visited = new boolean[index.listeners().length];
        for (int p : index.complex()) visited[p] = true;
        if (simple != null) for (int p : simple) visited[p] = true;
        for (int p = 0; p < visited.length; p++) {
            if (visited[p]) continue;
            Object inst = ((CriterionTrigger.Listener<?>) index.listeners()[p]).m_13685_();
            ItemPredicate pred = ((Predicates) inst).bons$inventoryIndexPredicates()[0];
            SHADOW_CHECKS.incrementAndGet();
            if (!stack.m_41619_() && pred.m_45049_(stack)) {
                long k = SHADOW_MISMATCHES.incrementAndGet();
                if (k <= 20) LOGGER.warn("Bons and Furious: vanilla_inventory_trigger_index shadow mismatch #{}: a left-out condition matches {}", k, stack);
            }
        }
    }

    /** True when a foreign mixin (not ours, not vetted) has members merged into one of the four classes. Checked once. */
    static boolean standingDown() {
        Boolean s = standDown;
        if (s == null) {
            String foreign = null;
            try {
                @SuppressWarnings("unchecked") Class<? extends Annotation> merged = (Class<? extends Annotation>) Class.forName("org.spongepowered.asm.mixin.transformer.meta.MixinMerged");
                Method mixinName = merged.getMethod("mixin");
                for (Class<?> c : new Class<?>[] {ItemPredicate.class, InventoryChangeTrigger.class, InventoryChangeTrigger.TriggerInstance.class, SimpleCriterionTrigger.class}) {
                    for (Method m : c.getDeclaredMethods()) {
                        Annotation a = m.getAnnotation(merged);
                        if (a == null) continue;
                        String name = String.valueOf(mixinName.invoke(a));
                        if (!name.startsWith(OWN_MIXINS) && !VETTED.contains(name)) foreign = name + " in " + c.getName();
                    }
                }
            } catch (Throwable t) {
                foreign = "the mixin check failed (" + t + ")";
            }
            s = foreign != null;
            standDown = s;
            if (s) LOGGER.info("Bons and Furious: vanilla_inventory_trigger_index steps aside: {} also changes the inventory trigger path; it is left as shipped", foreign);
        }
        return s;
    }
}
