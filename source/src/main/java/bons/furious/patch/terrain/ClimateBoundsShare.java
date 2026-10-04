package bons.furious.patch.terrain;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.Arrays;

/**
 * vanilla_climate_rtree_flat_bounds (Minecraft 1.21.1 world generation, both sides; tested build NeoForge 21.1.252):
 * one shared copy of each distinct bounds array.
 *
 * Every Climate.RTree node keeps its fourteen climate bounds in a long array (ClimateNodeBoundsMixin). A modded overworld
 * builds one climate tree per biome region, and regions that lay their biomes over the same climate layout produce leaves
 * with equal ranges and, from them, the same tree shape, so many of those arrays hold equal values. The arrays are filled
 * once, before the node is published, and never written again, so nodes whose bounds are equal can share one array: every
 * read returns the values it returned before, and the distance method itself is unchanged.
 *
 * The table holds the arrays weakly. An array that no node uses any more (its world was closed) can be collected, and its
 * entry is dropped on the next call. Calls only happen while climate trees are built, so the lock is not contended.
 *
 * Ported to 1.21.1: unchanged (pure Java, no Minecraft types). The only writer of a node's bounds array is still
 * ClimateNodeBoundsMixin's constructor hook (Node.&lt;init&gt; and parameterSpace are unchanged in 1.21.1), and the only
 * reader is its distance overwrite.
 */
public final class ClimateBoundsShare {
    private static final ReferenceQueue<long[]> QUEUE = new ReferenceQueue<>();
    private static Entry[] table = new Entry[1 << 12];
    private static int size;

    private static final class Entry extends WeakReference<long[]> {
        final int hash;
        Entry next;

        Entry(long[] value, int hash, Entry next) {
            super(value, QUEUE);
            this.hash = hash;
            this.next = next;
        }
    }

    private ClimateBoundsShare() {
    }

    /** An array with the same length and values as {@code bounds}: an earlier equal one when it is still in use, otherwise {@code bounds}. */
    public static synchronized long[] share(long[] bounds) {
        expunge();
        int h = Arrays.hashCode(bounds);
        Entry[] t = table;
        int i = index(h, t.length);
        for (Entry e = t[i]; e != null; e = e.next) {
            if (e.hash == h) {
                long[] v = e.get();
                if (v != null && Arrays.equals(v, bounds)) return v;
            }
        }
        t[i] = new Entry(bounds, h, t[i]);
        if (++size > t.length - (t.length >>> 2)) resize();
        return bounds;
    }

    /** Distinct arrays currently in the table (entries whose array was collected are counted until the next call). */
    public static synchronized int size() {
        return size;
    }

    private static int index(int h, int length) {
        return (h ^ (h >>> 16)) & (length - 1);
    }

    private static void expunge() {
        for (Object r; (r = QUEUE.poll()) != null; ) {
            Entry dead = (Entry) r;
            int i = index(dead.hash, table.length);
            Entry prev = null;
            for (Entry e = table[i]; e != null; prev = e, e = e.next) {
                if (e == dead) {
                    if (prev == null) table[i] = e.next;
                    else prev.next = e.next;
                    size--;
                    break;
                }
            }
        }
    }

    private static void resize() {
        Entry[] old = table;
        Entry[] t = new Entry[old.length << 1];
        for (Entry head : old) {
            for (Entry e = head; e != null; ) {
                Entry next = e.next;
                int i = index(e.hash, t.length);
                e.next = t[i];
                t[i] = e;
                e = next;
            }
        }
        table = t;
    }
}
