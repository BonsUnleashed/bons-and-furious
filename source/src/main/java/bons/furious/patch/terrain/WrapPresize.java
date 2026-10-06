package bons.furious.patch.terrain;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.world.level.levelgen.DensityFunction;

/**
 * vanilla_noise_wrap_presize (Minecraft 1.20.1 world generation, both sides): helper for NoiseChunkWrapPresizeMixin.
 *
 * Every NoiseChunk (one per generated chunk and one per terrain-height query) wraps the noise router through a private
 * table of the density functions it has already wrapped. ModernFix's perf.worldgen_allocation makes that table a
 * default-size Object2ObjectOpenHashMap. fastutil keeps no hash codes, so every time the table grows it re-hashes all
 * keys, and the keys are density-function records whose hash codes walk their whole subtree (the terrain splines
 * especially). Vanilla's HashMap keeps hashes but still copies its table on each growth.
 *
 * The constructor's still-empty table is replaced by one of exactly the same class, sized for the entry count the
 * previous NoiseChunk of the same RandomState reached. The table is private to the NoiseChunk and used only as a cache
 * by wrap (and read by Bons and Furious' own terrain_final_density_reuse, which combines its entries
 * order-independently), so the keys, values and objects are the same; only the number of growth steps changes.
 */
public final class WrapPresize {
    /** -Dbons_and_furious.noiseWrapPresize=false leaves the table as the constructor created it. */
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.noiseWrapPresize", "true"));

    /** RandomState gets an int field through RandomStateWrapSizeMixin: the largest table size seen for it. */
    public interface Holder {
        int bons$wrapSize();

        void bons$wrapSize(int size);
    }

    private WrapPresize() {
    }

    /**
     * The table to use instead of {@code current}, or {@code current} itself: only an empty table of exactly HashMap or
     * Object2ObjectOpenHashMap (vanilla's or ModernFix's) is replaced, and only once a size has been learned.
     */
    public static Map<DensityFunction, DensityFunction> presized(Map<DensityFunction, DensityFunction> current, Object randomState) {
        // Another mod also changes the call this hook sits on: leave the table as the constructor made it (CallSites).
        if (bons.furious.guard.CallSites.wrapPresizeForeign) return current;
        if (!enabled || !(randomState instanceof Holder holder) || current == null || !current.isEmpty()) return current;
        int expected = holder.bons$wrapSize();
        if (expected <= 16) return current;
        Class<?> type = current.getClass();
        if (type == Object2ObjectOpenHashMap.class) return new Object2ObjectOpenHashMap<>(expected);
        if (type == HashMap.class) return new HashMap<>((int) (expected / 0.75f) + 1);
        return current;
    }

    /** Remember the size the table reached in this constructor (a benign race: an int, the largest value wins mostly). */
    public static void learn(Map<DensityFunction, DensityFunction> table, Object randomState) {
        if (!(randomState instanceof Holder holder) || table == null) return;
        int size = table.size();
        if (size > holder.bons$wrapSize()) holder.bons$wrapSize(size);
    }
}
