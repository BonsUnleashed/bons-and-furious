package bons.furious.patch.bloodmagic_coo49;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import net.minecraft.core.BlockPos;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch bloodmagic_routing_visited_set (Blood Magic 3.3.8-50, CC BY 4.0; server; exact). Ours; no Blood
 * Magic code is carried. Idea: Collections Of Optimizations (bloodmagic.fastRoutingConnectivity), idea text only.
 *
 * What the mod does. TileMasterRoutingNode.tick (server, every max(1, 20 - upgrades) ticks) asks isConnected(new
 * LinkedList(), node) for every output and every input node of its network. isConnected is a depth-first search towards the
 * master: path.add(node) on entry, then for every connection of that node path.contains(connection) (skip when already
 * visited), the master test, and the recursion. The list is the search's visited set; it only grows, and LinkedList.contains
 * walks the whole list, so one check costs (nodes visited) x (connections walked) equality tests, and every input and output
 * node pays one check per routing tick.
 *
 * What the switch does (BmRoutingVisitedSetMixin, a redirect of the two NEWs that make those seed lists). A network of
 * fewer than THRESHOLD (16) nodes (the master's generalNodeList, every node it registered) gets the plain LinkedList as
 * shipped: its searches stay short, where a walk is cheaper than any set (either list gives the same search, so the size
 * only picks the faster one). A larger network's seed list is a VisitedList: a
 * LinkedList (every list operation as before) that, once it holds THRESHOLD elements,
 * all exactly BlockPos (checked when the set is built and for every later add), also keeps a HashSet of its elements.
 * contains(o) for o exactly a BlockPos is then answered from the set: BlockPos keeps Vec3i's equals / hashCode (coordinates,
 * consistent with each other) and is immutable, so the set holds an element equal to o exactly when the list does. Below the
 * threshold add and contains do what LinkedList does plus one field test each (a short walk is cheaper than hashing). Any
 * other element class, a null, a set size that differs from the list's, or any other list operation (which isConnected
 * never makes) turns the set off for that list for good, and contains walks the list. The search, its visit order (it
 * iterates the node's connections, not the path), its answers and the path's contents are unchanged.
 *
 * Runtime flag: -Dbons_and_furious.bloodmagicRoutingVisitedSet=false. SHADOW MODE (rigs):
 * -Dbons_and_furious.bloodmagicRoutingVisitedSet.shadow=true answers every set lookup by walking the list as well and
 * compares (SHADOW_CHECKS / SHADOW_MISMATCHES, WARN for the first 20); the list's answer is returned.
 */
public final class BmVisited {
    public static final String KEY = "bloodmagic_routing_visited_set";
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.bloodmagicRoutingVisitedSet", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.bloodmagicRoutingVisitedSet.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    private static volatile boolean announced;

    private BmVisited() {
    }

    /**
     * A seed list of TileMasterRoutingNode.tick, redirected: the plain LinkedList as shipped for a network (the master's
     * generalNodeList) of fewer than THRESHOLD nodes, where no search can need the set, and with the flag off.
     */
    @SuppressWarnings({"rawtypes"})
    public static LinkedList path(List<?> network) {
        if (!enabled || network == null || network.size() < VisitedList.THRESHOLD) return new LinkedList();
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: {} applies (Blood Magic's routing connectivity search keeps its visited nodes in a hash set)", KEY);
        }
        return new VisitedList();
    }

    /** A LinkedList that also answers contains from a hash set while that is provably the same answer (see the class comment). */
    public static final class VisitedList extends LinkedList<Object> {
        private static final long serialVersionUID = 1L;
        /** The list length from which contains is answered from the set. */
        public static final int THRESHOLD = 16;
        private transient HashSet<Object> set;      // null below the threshold and after off()
        private transient boolean walkOnly;          // for good: an element that is not exactly a BlockPos, or another operation

        private void off() {
            this.walkOnly = true;
            this.set = null;
        }

        /** For the harness: whether the list may still get the set (no other element class seen, no other operation). */
        public boolean plain() {
            return !this.walkOnly;
        }

        /** For the harness: whether contains is answered from the set now. */
        public boolean hashed() {
            return this.set != null;
        }

        @Override
        public boolean add(Object e) {
            boolean added = super.add(e);
            HashSet<Object> s = this.set;
            if (s != null) {
                if (e != null && e.getClass() == BlockPos.class) s.add(e);
                else off();
            } else if (!this.walkOnly && super.size() >= THRESHOLD) {
                build();
            }
            return added;
        }

        /** The set of the list's elements when every one is exactly a BlockPos; otherwise the list walks for good. */
        private void build() {
            HashSet<Object> s = new HashSet<>(64);
            for (Object x : super.toArray()) {   // toArray walks the nodes (no iterator)
                if (x == null || x.getClass() != BlockPos.class) {
                    off();
                    return;
                }
                s.add(x);
            }
            this.set = s;
        }

        @Override
        public boolean contains(Object o) {
            HashSet<Object> s = this.set;
            if (s != null && o != null && o.getClass() == BlockPos.class && s.size() == super.size()) {
                boolean answer = s.contains(o);
                if (SHADOW) {
                    boolean walked = super.contains(o);
                    SHADOW_CHECKS.incrementAndGet();
                    if (walked != answer && SHADOW_MISMATCHES.incrementAndGet() <= 20)
                        LOGGER.warn("Bons and Furious: {} SHADOW MISMATCH for {}: set {}, list {}", KEY, o, answer, walked);
                    return walked;
                }
                return answer;
            }
            return super.contains(o);
        }

        // every other mutator turns the set off (isConnected never calls them; this keeps any other use exact too)
        @Override public void add(int index, Object e) { off(); super.add(index, e); }
        @Override public void addFirst(Object e) { off(); super.addFirst(e); }
        @Override public void addLast(Object e) { off(); super.addLast(e); }
        @Override public boolean addAll(Collection<?> c) { off(); return super.addAll(c); }
        @Override public boolean addAll(int index, Collection<?> c) { off(); return super.addAll(index, c); }
        @Override public void push(Object e) { off(); super.push(e); }
        @Override public boolean offer(Object e) { off(); return super.offer(e); }
        @Override public boolean offerFirst(Object e) { off(); return super.offerFirst(e); }
        @Override public boolean offerLast(Object e) { off(); return super.offerLast(e); }
        @Override public Object set(int index, Object e) { off(); return super.set(index, e); }
        @Override public boolean remove(Object o) { off(); return super.remove(o); }
        @Override public Object remove(int index) { off(); return super.remove(index); }
        @Override public Object remove() { off(); return super.remove(); }
        @Override public Object removeFirst() { off(); return super.removeFirst(); }
        @Override public Object removeLast() { off(); return super.removeLast(); }
        @Override public boolean removeFirstOccurrence(Object o) { off(); return super.removeFirstOccurrence(o); }
        @Override public boolean removeLastOccurrence(Object o) { off(); return super.removeLastOccurrence(o); }
        @Override public Object poll() { off(); return super.poll(); }
        @Override public Object pollFirst() { off(); return super.pollFirst(); }
        @Override public Object pollLast() { off(); return super.pollLast(); }
        @Override public Object pop() { off(); return super.pop(); }
        @Override public void clear() { off(); super.clear(); }
        @Override public boolean removeIf(Predicate<? super Object> filter) { off(); return super.removeIf(filter); }
        @Override public boolean removeAll(Collection<?> c) { off(); return super.removeAll(c); }
        @Override public boolean retainAll(Collection<?> c) { off(); return super.retainAll(c); }
        @Override public void replaceAll(UnaryOperator<Object> operator) { off(); super.replaceAll(operator); }
        @Override public void sort(Comparator<? super Object> c) { off(); super.sort(c); }
        @Override public Iterator<Object> iterator() { off(); return super.iterator(); }
        @Override public ListIterator<Object> listIterator(int index) { off(); return super.listIterator(index); }
        @Override public Iterator<Object> descendingIterator() { off(); return super.descendingIterator(); }
        // a clone would share the set object: both lists fall back to the walk
        @Override public Object clone() { off(); return super.clone(); }
    }
}
