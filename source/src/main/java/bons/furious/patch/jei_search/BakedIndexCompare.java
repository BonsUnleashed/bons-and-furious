package bons.furious.patch.jei_search;

import bons.furious.mixin.jei_search.BakedIndexAccessor;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.util.Arrays;

/**
 * jei_baked_index_background_grams (JEI 19.51.0.418 for Minecraft 1.21.1 / NeoForge, tested build
 * jei-1.21.1-neoforge-19.51.0.418.jar; client): shadow-mode comparison of two BakedSubstringIndex objects (the original
 * build's and ours). Equal means: the same key and value objects in the same positions, the same deduplicateResults flag,
 * and gram maps that hold the same keys in the same iteration order (with equal capacity that is the same internal
 * layout) with equal posting arrays. Returns null when equal, else a short description.
 *
 * Ported to 1.21.1: no change (BakedSubstringIndex's four fields are unchanged in JEI 19.51.0.418).
 */
public final class BakedIndexCompare {
    private BakedIndexCompare() {
    }

    public static String diff(Object original, Object ours) {
        BakedIndexAccessor a = (BakedIndexAccessor) original, b = (BakedIndexAccessor) ours;
        String[] ka = a.bons$indexKeys(), kb = b.bons$indexKeys();
        if (ka.length != kb.length) return "key count " + ka.length + " vs " + kb.length;
        for (int i = 0; i < ka.length; i++) if (ka[i] != kb[i]) return "key object at " + i;
        Object[] va = a.bons$indexValues(), vb = b.bons$indexValues();
        if (va.length != vb.length) return "value count " + va.length + " vs " + vb.length;
        for (int i = 0; i < va.length; i++) if (va[i] != vb[i]) return "value object at " + i;
        if (a.bons$deduplicateResults() != b.bons$deduplicateResults()) return "deduplicateResults flag";
        Long2ObjectOpenHashMap<int[]> ma = a.bons$entriesByGram(), mb = b.bons$entriesByGram();
        if (ma.size() != mb.size()) return "gram count " + ma.size() + " vs " + mb.size();
        ObjectIterator<Long2ObjectMap.Entry<int[]>> ia = ma.long2ObjectEntrySet().iterator(), ib = mb.long2ObjectEntrySet().iterator();
        while (ia.hasNext()) {
            if (!ib.hasNext()) return "gram iteration ends early";
            Long2ObjectMap.Entry<int[]> ea = ia.next(), eb = ib.next();
            if (ea.getLongKey() != eb.getLongKey()) return "gram order differs at gram " + Long.toHexString(ea.getLongKey());
            if (!Arrays.equals(ea.getValue(), eb.getValue())) return "postings of gram " + Long.toHexString(ea.getLongKey());
        }
        return ib.hasNext() ? "gram iteration longer" : null;
    }
}
