package bons.furious.patch.modernfix_bake;

import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectListIterator;
import java.util.Arrays;
import java.util.BitSet;
import java.util.NoSuchElementException;
import java.util.function.Consumer;
import net.minecraft.client.resources.model.ModelResourceLocation;

/**
 * Bons and Furious switch modernfix_bake_location_order (ModernFix, LGPL-3.0; 1.21.1 tested build
 * modernfix-neoforge-5.27.24+mc1.21.1; client). No ModernFix code.
 *
 * <p>ModernFix's emulated ModelEvent.ModifyBakingResult / BakingCompleted (ModelBakeEventHelper) collects every top-level
 * model location of the game - one ModelResourceLocation per block state, one inventory location per item and per item
 * model file, plus the bakery's keys - into an ObjectLinkedOpenHashSet, and hands that set to every mod's handler as the
 * model map's keySet() / entrySet() / replaceAll / Sets.filter views. Handlers that walk it completely (on 1.20.1: Alex's
 * Mobs, MrCrayfish's Furniture, Mekanism's replaceAll, Refined Storage's filtered view; ~0.35-0.6 s each in our test
 * rig) pay for a linked hash set of millions of entries walked by following its link array through tens of MB of key and
 * link tables: two cache misses per element.
 *
 * <p>This set IS that ObjectLinkedOpenHashSet (same class hierarchy, every method of fastutil 8.5.12 runs as before; adds,
 * lookups, sizes and hashCode come from the superclass) and additionally remembers, in an array, every key that add()
 * really inserted, in the order it inserted it. ObjectLinkedOpenHashSet.add puts a new key at the end of its link chain,
 * and addAll calls add once per element (its own addAll only pre-sizes, then java.util.AbstractCollection.addAll loops
 * over add), rehashes keep the link order, so as long as nothing else changes the set the
 * array holds exactly the link order. Then iterator() and forEach(...) walk the array (sequential memory) instead of the
 * link chain: the same elements, the same objects, in the same order. The spliterator of the superclass is built on
 * iterator(), so streams see the same order too.
 *
 * <p>Out of step = the array is not used: any other mutator (remove, removeFirst/Last, addAndMoveToFirst/Last, clear, an
 * iterator's remove) marks the set diverged for good, addOrGet appends exactly when it inserts, and every iterator() /
 * forEach call checks that the array still has size() entries; otherwise the superclass's own iterator and forEach run.
 * ModernFix itself never changes the set after its constructor and exposes it only through read-only views
 * (Collections.unmodifiableSet, Sets.filter inside unmodifiableSet, its entry set without mutators, its own replaceAll and
 * containsKey), so in practice the array is always used.
 *
 * <p>The iterator implements ObjectListIterator exactly like the superclass's SetIterator: next/previous/hasNext/
 * hasPrevious/nextIndex/previousIndex/forEachRemaining/remove (remove deletes the last returned key from the set and
 * marks the set diverged; later steps of the same iterator skip removed entries, as the link chain would no longer hold
 * them); set/add/skip/back keep fastutil's interface defaults.
 *
 * <p>Ported to 1.21.1: element type ModelResourceLocation (a record since 1.21, no longer a ResourceLocation subclass;
 * ModernFix 5.27.24's set is typed Set&lt;ModelResourceLocation&gt;); the constructor's extra item-model-file locations
 * arrive through add() like every other location. fastutil 8.5.12 keeps 8.5.9's add/addAll/addOrGet/iterator/forEach
 * bytecode; its public ensureCapacity only resizes (link order kept).
 */
public final class LocationOrderSet extends ObjectLinkedOpenHashSet<ModelResourceLocation> {
    private Object[] order;
    private int count;
    private boolean diverged;

    public LocationOrderSet(int expected) {
        super(expected);
        order = new Object[Math.max(16, expected)];
        BakeLocations.SETS.incrementAndGet();
    }

    /** True when the array holds exactly the link order (no other mutator ran and every insertion was recorded). */
    public boolean inStep() {
        return !diverged && count == size();
    }

    private void append(ModelResourceLocation k) {
        if (count == order.length) order = Arrays.copyOf(order, order.length + (order.length >> 1) + 16);
        order[count++] = k;
    }

    @Override
    public boolean add(ModelResourceLocation k) {
        if (!super.add(k)) return false;
        if (!diverged) append(k);
        return true;
    }

    @Override
    public ModelResourceLocation addOrGet(ModelResourceLocation k) {
        int before = size();
        ModelResourceLocation r = super.addOrGet(k);
        if (size() != before && !diverged) append(k);   // inserted: k went to the end of the link chain
        return r;
    }

    @Override
    public boolean remove(Object k) {
        diverged = true;
        return super.remove(k);
    }

    @Override
    public ModelResourceLocation removeFirst() {
        diverged = true;
        return super.removeFirst();
    }

    @Override
    public ModelResourceLocation removeLast() {
        diverged = true;
        return super.removeLast();
    }

    @Override
    public boolean addAndMoveToFirst(ModelResourceLocation k) {
        diverged = true;
        return super.addAndMoveToFirst(k);
    }

    @Override
    public boolean addAndMoveToLast(ModelResourceLocation k) {
        diverged = true;
        return super.addAndMoveToLast(k);
    }

    @Override
    public void clear() {
        diverged = true;
        super.clear();
    }

    @Override
    public LocationOrderSet clone() {
        LocationOrderSet c = (LocationOrderSet) super.clone();
        c.order = order.clone();
        return c;
    }

    @Override
    public ObjectListIterator<ModelResourceLocation> iterator() {
        if (!BakeLocations.enabled || !inStep()) {
            BakeLocations.FALLBACKS.incrementAndGet();
            return super.iterator();
        }
        if (BakeLocations.SHADOW) {
            BakeLocations.shadow(this, order, count);
            return super.iterator();   // shadow mode: the game keeps the original iteration
        }
        BakeLocations.announce();
        BakeLocations.ITERATIONS.incrementAndGet();
        return new OrderIterator();
    }

    /** The superclass's own iterator over the link chain (shadow mode, self-test, harness). */
    public ObjectListIterator<ModelResourceLocation> linkIterator() {
        return super.iterator();
    }

    /** The array iterator without the switch, counters or step check (self-test, harness). */
    public ObjectListIterator<ModelResourceLocation> arrayIterator() {
        return new OrderIterator();
    }

    @Override
    public void forEach(Consumer<? super ModelResourceLocation> action) {
        if (!BakeLocations.enabled || !inStep()) {
            BakeLocations.FALLBACKS.incrementAndGet();
            super.forEach(action);
            return;
        }
        if (BakeLocations.SHADOW) {
            BakeLocations.shadow(this, order, count);
            super.forEach(action);
            return;
        }
        BakeLocations.announce();
        BakeLocations.ITERATIONS.incrementAndGet();
        // as the superclass: the successor is decided before the action runs (an add by the action while the last
        // element is current is not visited; an add before that is)
        int i = 0;
        while (i < count) {
            boolean more = i + 1 < count;
            @SuppressWarnings("unchecked") ModelResourceLocation k = (ModelResourceLocation) order[i];
            action.accept(k);
            if (!more) break;
            i++;
        }
    }

    /** Linked-order iterator over the array, with SetIterator's list-iterator semantics. */
    final class OrderIterator implements ObjectListIterator<ModelResourceLocation> {
        private int cursor;          // array index of the next candidate
        private int lastRet = -1;    // array index of the element last returned by next/previous, -1 after remove
        private BitSet removed;      // entries removed through this iterator
        private int removedBelow;    // removed entries with index < cursor

        private boolean gone(int i) {
            return removed != null && removed.get(i);
        }

        private int nextLive(int from) {
            int i = from;
            while (i < count && gone(i)) i++;
            return i;
        }

        private int prevLive(int from) {
            int i = from;
            while (i >= 0 && gone(i)) i--;
            return i;
        }

        @Override
        public boolean hasNext() {
            return nextLive(cursor) < count;
        }

        @Override
        public boolean hasPrevious() {
            return prevLive(cursor - 1) >= 0;
        }

        @Override
        public ModelResourceLocation next() {
            int i = nextLive(cursor);
            if (i >= count) throw new NoSuchElementException();
            if (removed != null) removedBelow += countRemoved(cursor, i);   // removed entries stepped over
            cursor = i + 1;
            lastRet = i;
            return (ModelResourceLocation) order[i];
        }

        @Override
        public ModelResourceLocation previous() {
            int i = prevLive(cursor - 1);
            if (i < 0) throw new NoSuchElementException();
            if (removed != null) removedBelow -= countRemoved(i, cursor);
            cursor = i;
            lastRet = i;
            return (ModelResourceLocation) order[i];
        }

        private int countRemoved(int from, int to) {   // removed entries in [from, to)
            int n = 0;
            for (int j = removed.nextSetBit(from); j >= 0 && j < to; j = removed.nextSetBit(j + 1)) n++;
            return n;
        }

        @Override
        public int nextIndex() {
            return cursor - removedBelow;
        }

        @Override
        public int previousIndex() {
            return cursor - removedBelow - 1;
        }

        @Override
        public void forEachRemaining(Consumer<? super ModelResourceLocation> action) {
            while (true) {
                int i = nextLive(cursor);
                if (i >= count) return;
                if (removed != null) removedBelow += countRemoved(cursor, i);
                cursor = i + 1;
                lastRet = i;
                action.accept((ModelResourceLocation) order[i]);
            }
        }

        @Override
        public void remove() {
            if (lastRet < 0) throw new IllegalStateException();
            int i = lastRet;
            lastRet = -1;
            diverged = true;   // later iterator()/forEach calls use the link chain
            LocationOrderSet.super.remove(order[i]);
            if (removed == null) removed = new BitSet();
            removed.set(i);
            if (i < cursor) removedBelow++;
        }
    }
}
