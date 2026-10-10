package bons.furious.patch.vanilla_inventory_index;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.logging.LogUtils;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.advancements.CriterionTrigger;
import net.minecraft.advancements.critereon.InventoryChangeTrigger;
import net.minecraft.advancements.critereon.ItemPredicate;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

/**
 * Bons and Furious switch vanilla_inventory_trigger_index (Minecraft 1.21.1 with NeoForge 21.1.252; server side incl. the
 * integrated server). Mojang member names. Idea: InventoryAdvancementAccelerator (a per-player item index of the
 * inventory_changed conditions), idea text only; the exact form (no sampling, no periodic full scans, vanilla order kept)
 * is ours.
 *
 * What vanilla does. Every inventory change of a player (each slot a container or pickup touches) calls
 * InventoryChangeTrigger.trigger, and SimpleCriterionTrigger.trigger builds a loot context and then walks the player's
 * whole listener HashSet: for each listener it evaluates `predicate.test(instance)` (here instance.matches(inventory,
 * stack, full, empty, occupied)) and, when that holds, the instance's optional player condition, and grants the matches
 * afterwards, in HashSet order. A large pack registers tens of thousands of inventory_changed criteria (recipe
 * advancements included); a player still holds thousands of them.
 *
 * What the switch does. Per player (PlayerAdvancements) the trigger keeps an index of its listener set: the listeners in
 * the set's own iteration order, the positions of "simple" ones under each item key of their condition, and the
 * positions of all others. A listener is simple when its instance is exactly InventoryChangeTrigger.TriggerInstance with
 * exactly one item condition of exactly class ItemPredicate whose item set is present and a HolderSet.Direct (an explicit
 * item list, not a tag) whose every element is a plain Holder.Reference with its registry key bound. When trigger walks
 * the set, the iterator it gets visits only the other listeners and the simple ones listed under the changed stack's item
 * key (none for an empty stack), in increasing set position, so in exactly the set's order; vanilla's own loop still
 * evaluates each of them and grants as before.
 *
 * Why the result is identical. For a simple listener vanilla's test is `slots.matches(full, empty, occupied) (pure int
 * checks) && !stack.isEmpty() && item.test(stack)`, and ItemPredicate.test first asks `stack.is(items)` =
 * items.contains(stack.getItemHolder()) before anything else (count, components, sub-predicates come after). HolderSet
 * .Direct.contains looks the holder up in Set.copyOf(contents) (built once, lazily), and NeoForge 21's Holder.Reference
 * equals/hashCode compare the registry key by identity, so for a set of plain references the answer is true exactly when
 * the stack's item holder has the key of one of them: for an empty stack or an item key outside the set the test answers
 * false with no side effect, and the player condition is not evaluated (short-circuit), so leaving such a listener out
 * changes nothing. Every listener vanilla could match is visited, in the same order, with the same calls, so matches,
 * grants, their order and the loot context's random draws are identical. The index is rebuilt when the set changes: every
 * write of a trigger's listener map goes through addPlayerListener / removePlayerListener / removePlayerListeners, and
 * each bumps the trigger's version. A change during the walk throws ConcurrentModificationException from next(), as the
 * HashSet iterator would. A stack whose item holder has no bound key (an unregistered item) gets vanilla's own iterator.
 * The switch stands down (INFO once) if any other mixin than this switch's own (and the vetted VETTED list) has members
 * merged into ItemPredicate, InventoryChangeTrigger, its TriggerInstance or Slots, SimpleCriterionTrigger,
 * HolderSet.Direct or Holder.Reference.
 *
 * -Dbons_and_furious.inventoryTriggerIndex=false switches it off at run time.
 * -Dbons_and_furious.inventoryTriggerIndex.shadow=true (verification runs only): the original full iterator is used, and
 * for every simple listener the index would leave out its item condition is asked (ItemPredicate.test) and must answer
 * false (SHADOW_CHECKS / SHADOW_MISMATCHES, WARN for the first 20).
 *
 * Ported to 1.21.1: TriggerInstance and ItemPredicate are records (public accessors, no accessor mixins; a record class is
 * final, so "exactly ItemPredicate" holds by itself) and the item condition is an Optional HolderSet<Item> tested through
 * ItemStack.is(HolderSet) instead of a TagKey plus a Set<Item>: the index is keyed by the elements' registry keys (by
 * identity, as NeoForge's Holder.Reference.equals compares them), and a condition over a tag (HolderSet.Named), a custom
 * NeoForge HolderSet or any non-reference element is simply not simple (always visited). The slot checks moved into the
 * Slots record. BCLib and Tinkers' Jewelry, whose 1.20.1 mixins were vetted, have no build among the pinned 1.21.1
 * targets: no foreign mixin is vetted here, and BCLib's {shears} exception is gone with it; the only vetted name is Bons'
 * own vanilla_tag_membership_ids mixin on Holder.Reference (it touches is(TagKey) only). A HolderSet.Direct may list an
 * item twice: each listener is filed once per distinct key.
 */
public final class ListenerIndex {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.inventoryTriggerIndex", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.inventoryTriggerIndex.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    /** Test and log support: index builds, indexed walks, listeners left out. */
    public static final AtomicLong BUILDS = new AtomicLong(), WALKS = new AtomicLong(), SKIPPED = new AtomicLong();
    static final String OWN_MIXINS = "bons.furious.mixin.vanilla_inventory_index.";
    /**
     * Read and vetted: Bons' own vanilla_tag_membership_ids mixin, which redirects only the Set.contains call inside
     * Holder.Reference.is(TagKey) (tag membership); the item test here never calls is(TagKey), and the mixin leaves
     * equals, hashCode and the key alone.
     */
    static final Set<String> VETTED = Set.of("bons.furious.mixin.tag_ids.HolderReferenceTagIdsMixin");
    private static volatile Boolean standDown;
    private static volatile boolean announced;
    private static final ThreadLocal<Context> CURRENT = new ThreadLocal<>();

    /** Implemented by SimpleCriterionTrigger (version of its listener map) and its per-player index cache. */
    public interface Versioned {
        int bons$listenerVersion();

        Map<Object, Index> bons$listenerIndexes();
    }

    /** The inventory change being triggered on this thread. */
    record Context(Object trigger, Object advancements, ItemStack stack, Context previous) {
    }

    /** One player's listener set of one trigger, indexed. Immutable after construction. */
    public record Index(Set<?> set, int version, Object[] listeners, int[] complex, Map<ResourceKey<Item>, int[]> byItem) {
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

    /** SimpleCriterionTrigger.trigger: its listener-set iterator. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static Iterator iterator(SimpleCriterionTrigger<?> trigger, Set set, Operation<Iterator> original) {
        Context c = CURRENT.get();
        if (!enabled || c == null || c.trigger() != trigger || !(trigger instanceof InventoryChangeTrigger) || standingDown()) {
            return original.call(set);
        }
        ItemStack stack = c.stack();
        ResourceKey<Item> key = null;
        if (!stack.isEmpty()) {
            // the holder vanilla's ItemPredicate.test hands to HolderSet.contains (ItemStack.is(HolderSet)); getKey is
            // NeoForge's non-throwing key getter (unwrapKey and key() throw for an unbound holder)
            key = stack.getItemHolder().getKey();
            if (key == null) return original.call(set);   // unregistered item: vanilla's own walk (and its errors)
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
        int[] simple = key == null ? null : index.byItem().get(key);
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
        Map<ResourceKey<Item>, int[]> byItem = new IdentityHashMap<>();
        Map<ResourceKey<Item>, Integer> counts = new IdentityHashMap<>();
        Set<ResourceKey<Item>>[] keys = new Set[n];
        for (int i = 0; i < n; i++) {
            keys[i] = simpleKeys(((CriterionTrigger.Listener<?>) listeners[i]).trigger());
            if (keys[i] == null) complex[nc++] = i;
            else for (ResourceKey<Item> key : keys[i]) counts.merge(key, 1, Integer::sum);
        }
        for (Map.Entry<ResourceKey<Item>, Integer> e : counts.entrySet()) byItem.put(e.getKey(), new int[e.getValue()]);
        Map<ResourceKey<Item>, Integer> fill = new IdentityHashMap<>();
        for (int i = 0; i < n; i++) {
            if (keys[i] == null) continue;
            for (ResourceKey<Item> key : keys[i]) {
                int k = fill.merge(key, 1, Integer::sum) - 1;
                byItem.get(key)[k] = i;
            }
        }
        return new Index(set, version, listeners, java.util.Arrays.copyOf(complex, nc), byItem);
    }

    /**
     * The distinct registry keys (by identity) of a simple listener's item holders, or null when the listener is not
     * simple: its instance must be exactly a TriggerInstance with one ItemPredicate whose item set is a HolderSet.Direct
     * of plain Holder.References with bound keys.
     */
    static Set<ResourceKey<Item>> simpleKeys(Object instance) {
        if (instance == null || instance.getClass() != InventoryChangeTrigger.TriggerInstance.class) return null;
        List<ItemPredicate> conditions = ((InventoryChangeTrigger.TriggerInstance) instance).items();
        if (conditions == null || conditions.size() != 1) return null;
        ItemPredicate p = conditions.get(0);
        if (p == null || p.getClass() != ItemPredicate.class) return null;
        Optional<HolderSet<Item>> items = p.items();
        if (items == null || items.isEmpty()) return null;
        HolderSet<Item> set = items.get();
        if (set == null || set.getClass() != HolderSet.Direct.class) return null;
        Set<ResourceKey<Item>> keys = Collections.newSetFromMap(new IdentityHashMap<>());
        for (int i = 0, size = set.size(); i < size; i++) {
            Holder<Item> h = set.get(i);
            if (h == null || h.getClass() != Holder.Reference.class) return null;
            ResourceKey<Item> key = h.getKey();   // the reference's key field (null while unbound: then not simple)
            if (key == null) return null;
            keys.add(key);
        }
        return keys;
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
            Object inst = ((CriterionTrigger.Listener<?>) index.listeners()[p]).trigger();
            ItemPredicate pred = ((InventoryChangeTrigger.TriggerInstance) inst).items().get(0);
            SHADOW_CHECKS.incrementAndGet();
            if (!stack.isEmpty() && pred.test(stack)) {
                long k = SHADOW_MISMATCHES.incrementAndGet();
                if (k <= 20) LOGGER.warn("Bons and Furious: vanilla_inventory_trigger_index shadow mismatch #{}: a left-out condition matches {}", k, stack);
            }
        }
    }

    /** True when a foreign mixin (not ours) has members merged into one of the checked classes. Checked once. */
    static boolean standingDown() {
        Boolean s = standDown;
        if (s == null) {
            String foreign = null;
            try {
                @SuppressWarnings("unchecked") Class<? extends Annotation> merged = (Class<? extends Annotation>) Class.forName("org.spongepowered.asm.mixin.transformer.meta.MixinMerged");
                Method mixinName = merged.getMethod("mixin");
                for (Class<?> c : new Class<?>[] {ItemPredicate.class, InventoryChangeTrigger.class, InventoryChangeTrigger.TriggerInstance.class,
                        InventoryChangeTrigger.TriggerInstance.Slots.class, SimpleCriterionTrigger.class, HolderSet.Direct.class, Holder.Reference.class}) {
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
