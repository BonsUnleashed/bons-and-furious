package bons.furious.patch.zeta_lean;

import com.mojang.logging.LogUtils;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Set;
import org.slf4j.Logger;

/**
 * Bons and Furious switch zeta_structure_function_array (Zeta 1.1-40 for Minecraft 1.21.1 NeoForge; server side, including
 * the integrated server, worldgen threads).
 *
 * What Zeta does. StructureBlockReplacementHandler.getResultingBlockState runs for every block a structure places while a
 * structure is active on that thread and walks its private static HashSet of replacement functions (Quark's gold bars and
 * variant chests) with the set's iterator, returning the first non-null function result. A HashSet iterator scans the
 * buckets of its table (16 slots for two functions) to find each element, on every block.
 *
 * What the switch does. The iterator that loop gets is one over an array copy of the set, taken in the set's own iteration
 * order (toArray walks the set's iterator) and taken again whenever the set object, its size or the epoch bumped by
 * addReplacement differs from the copy's; so the same functions come in the same order and the loop returns the same
 * first non-null result. Zeta's loop itself is unchanged. Thread-safe: an immutable snapshot published through a volatile
 * field.
 *
 * -Dbons_and_furious.zetaStructureFunctionArray=false switches it off at run time.
 */
public final class StructureFunctions {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.zetaStructureFunctionArray", "true"));
    private static volatile int epoch;
    private static volatile Snapshot snapshot;
    private static volatile boolean announced;

    /** An array copy of one set object at one size and epoch, in the set's iteration order. */
    private record Snapshot(Set<?> set, int size, int epoch, Object[] items) {
    }

    private StructureFunctions() {
    }

    /** StructureBlockReplacementHandler.addReplacement ran: the next lookup copies the set again. */
    public static synchronized void bump() {
        epoch = epoch + 1;
    }

    /** getResultingBlockState: its functions.iterator() call. */
    public static Iterator<?> iterator(Set<?> functions) {
        if (!enabled) return functions.iterator();
        Snapshot s = snapshot;
        int e = epoch;
        if (s == null || s.set() != functions || s.size() != functions.size() || s.epoch() != e) {
            s = new Snapshot(functions, functions.size(), e, functions.toArray());
            snapshot = s;
            if (!announced) {
                announced = true;
                LOGGER.info("Bons and Furious: zeta_structure_function_array applies (Zeta's structure block replacement walks an array copy of its {} function(s))", s.items().length);
            }
        }
        return new ArrayIt(s.items());
    }

    /** A plain forward iterator over the snapshot (remove() unsupported, as Zeta's loop never removes). */
    private static final class ArrayIt implements Iterator<Object> {
        private final Object[] items;
        private int next;

        ArrayIt(Object[] items) {
            this.items = items;
        }

        @Override
        public boolean hasNext() {
            return next < items.length;
        }

        @Override
        public Object next() {
            if (next >= items.length) throw new NoSuchElementException();
            return items[next++];
        }
    }
}
