package bons.furious.patch.embeddium_meshing;

import java.util.List;
import net.minecraft.util.random.WeightedEntry;

/**
 * Bons and Furious switch embeddium_weighted_pick_table (Minecraft 1.20.1 WeightedBakedModel as overwritten by Embeddium
 * 0.3.31, client).
 *
 * Embeddium's WeightedBakedModelMixin picks a weighted block-model variant with
 * {@code getAt(list, Math.abs((int) random.nextLong()) % totalWeight)}: a walk that subtracts each entry's weight from the
 * drawn value until it goes negative and returns that entry (null if the list runs out). It does that for every face
 * query and every render-type query of the block (Fusion's combined models ask both, per face), and xali's resource packs
 * give sand 28 variants, netherrack 36, cobblestone 17: a pointer walk through Wrapper and Weight objects per pick.
 *
 * The table holds the same entries and their running weight sums cum[i] = w0 + ... + wi, built when the model is
 * constructed from the model's own list. The walk stops at the first i where value - cum[i] < 0, i.e. the first i with
 * cum[i] > value, because Weight never holds a negative number (Weight.validateWeight): sums only grow, and zero-weight
 * entries are skipped exactly as the walk skips them. A negative value (Math.abs(Integer.MIN_VALUE) % total) makes the
 * walk return the first entry, and the table does the same; a value at or above the total makes both return null. The
 * random draw itself stays in Embeddium's code, so the random sequence is unchanged. The table is only built when its
 * sum equals the model's totalWeight, and only used for that model's own list object.
 */
public final class WeightedPickTable {
    /** Runtime switch (the config switch acts when classes are transformed). -Dbons_and_furious.embeddiumWeightedPickTable=false also turns it off. */
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.embeddiumWeightedPickTable", "true"));
    private static final int LINEAR_MAX = 8;

    public final List<?> list;
    private final int[] cumulative;
    private final WeightedEntry[] entries;

    private WeightedPickTable(List<?> list, int[] cumulative, WeightedEntry[] entries) {
        this.list = list;
        this.cumulative = cumulative;
        this.entries = entries;
    }

    /** The table for a model's list and totalWeight, or null when the list is empty or does not add up to the total. */
    public static WeightedPickTable build(List<? extends WeightedEntry> list, int totalWeight) {
        try {
            int n = list == null ? 0 : list.size();
            if (n == 0) return null;
            int[] cum = new int[n];
            WeightedEntry[] e = new WeightedEntry[n];
            long sum = 0;
            for (int i = 0; i < n; i++) {
                WeightedEntry entry = list.get(i);
                int w = entry.m_142631_().m_146281_();
                if (w < 0) return null;
                sum += w;
                if (sum > Integer.MAX_VALUE) return null;
                cum[i] = (int) sum;
                e[i] = entry;
            }
            return sum == totalWeight ? new WeightedPickTable(list, cum, e) : null;
        } catch (RuntimeException ex) {
            return null;
        }
    }

    /** Embeddium's {@code getAt(list, value)} for this table's list. */
    @SuppressWarnings("unchecked")
    public <T extends WeightedEntry> T pick(int value) {
        int[] cum = this.cumulative;
        if (value < 0 || cum[0] > value) return (T) entries[0];   // also the common case of a dominant first variant
        int n = cum.length;
        if (n <= LINEAR_MAX) {
            for (int i = 0; i < n; i++) {
                if (cum[i] > value) return (T) entries[i];
            }
            return null;
        }
        if (cum[n - 1] <= value) return null;
        int lo = 0, hi = n - 1;                 // invariant: cum[hi] > value; answer in [lo, hi]
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (cum[mid] > value) hi = mid;
            else lo = mid + 1;
        }
        return (T) entries[lo];
    }
}
