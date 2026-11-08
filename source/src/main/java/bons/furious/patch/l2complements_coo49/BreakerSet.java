package bons.furious.patch.l2complements_coo49;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.AbstractSet;
import java.util.Arrays;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch l2complements_breaker_set_concurrent (L2 Complements, LGPL-2.1; 1.21.1 tested build 3.1.3; both
 * sides; kind fix). None of L2 Complements' code is carried. Ours, idea from Collections Of Optimizations
 * (l2complements.fixBreakerSetRace), idea text only.
 *
 * The bug: RangeDiggingEnchantment keeps the players that are chain-digging right now in a static HashSet (BREAKER).
 * execute() adds and removes under synchronized(BREAKER), but isSuppressed() reads it with no lock, from L2 Complements'
 * BlockMixin hook on Block.spawnDestroyParticles, which also runs on the client thread when the client predicts a block
 * break. In single player (and on a LAN host) the client thread and the integrated server thread use the same static set,
 * so the client reads a HashMap that the server thread is changing; while a HashMap grows, a reader can find a key that is
 * in the set missing (it sees the new, still empty bucket array), and the memory model does not promise it sees a finished
 * table at all.
 *
 * The fix (BreakerSetMixin, at the end of the class's static initialiser): BREAKER becomes a SnapshotSet, a copy-on-write
 * set: contains() reads one immutable array (the few players chain-digging at once) with acquire ordering, add() and
 * remove() publish a changed copy with release ordering, inside execute()'s synchronized(BREAKER) as before. Same answers
 * for contains/add/remove of player UUIDs (never null), and that synchronized block still makes the add-run-remove of one
 * chain atomic against another; unlocked readers now always see a whole set. Decided once, when the class loads.
 *
 * Flag: -Dbons_and_furious.l2complementsBreakerSetConcurrent=false keeps the shipped HashSet.
 */
public final class BreakerSet {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.l2complementsBreakerSetConcurrent", "true"));

    private BreakerSet() {
    }

    /** The set BREAKER holds from now on (the shipped one, still empty at this point, copied into it). */
    public static <T> Set<T> replace(Set<T> shipped) {
        Set<T> set = newSet();
        if (shipped != null) set.addAll(shipped);
        LOGGER.info("Bons and Furious: l2complements_breaker_set_concurrent applies (L2 Complements' chain-digging player set is safe to read from the client thread)");
        return set;
    }

    /** A new, empty set of the kind BREAKER becomes (for the harness too). */
    public static <T> Set<T> newSet() {
        return new SnapshotSet<>();
    }

    /** Copy-on-write set for a handful of elements: lock-free reads of an immutable array (acquire), writes publish a changed
     *  copy (release). Writers synchronize on the set themselves, as with the shipped HashSet (RangeDiggingEnchantment.execute
     *  makes every add and remove inside synchronized(BREAKER)). */
    public static final class SnapshotSet<E> extends AbstractSet<E> {
        private static final Object[] EMPTY = new Object[0];
        private static final VarHandle ITEMS;

        static {
            try {
                ITEMS = MethodHandles.lookup().findVarHandle(SnapshotSet.class, "items", Object[].class);
            } catch (ReflectiveOperationException e) {
                throw new ExceptionInInitializerError(e);
            }
        }

        @SuppressWarnings("unused")
        private Object[] items = EMPTY;

        private Object[] snapshot() {
            return (Object[]) ITEMS.getAcquire(this);
        }

        @Override
        public boolean contains(Object o) {
            for (Object x : snapshot()) if (Objects.equals(x, o)) return true;
            return false;
        }

        @Override
        public boolean add(E e) {
            Object[] a = snapshot();
            for (Object x : a) if (Objects.equals(x, e)) return false;
            Object[] b = Arrays.copyOf(a, a.length + 1);
            b[a.length] = e;
            ITEMS.setRelease(this, b);
            return true;
        }

        @Override
        public boolean remove(Object o) {
            Object[] a = snapshot();
            for (int i = 0; i < a.length; i++) {
                if (!Objects.equals(a[i], o)) continue;
                Object[] b = a.length == 1 ? EMPTY : new Object[a.length - 1];
                System.arraycopy(a, 0, b, 0, i);
                System.arraycopy(a, i + 1, b, i, a.length - i - 1);
                ITEMS.setRelease(this, b);
                return true;
            }
            return false;
        }

        @Override
        public void clear() {
            ITEMS.setRelease(this, EMPTY);
        }

        @Override
        public int size() {
            return snapshot().length;
        }

        @Override
        public Iterator<E> iterator() {
            Object[] a = snapshot();
            return new Iterator<>() {
                private int i;

                @Override
                public boolean hasNext() {
                    return i < a.length;
                }

                @Override
                @SuppressWarnings("unchecked")
                public E next() {
                    if (i >= a.length) throw new NoSuchElementException();
                    return (E) a[i++];
                }

                @Override
                public void remove() {
                    if (i == 0) throw new IllegalStateException();
                    SnapshotSet.this.remove(a[i - 1]);
                }
            };
        }
    }
}
