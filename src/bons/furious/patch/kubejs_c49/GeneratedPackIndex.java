package bons.furious.patch.kubejs_c49;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.IoSupplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch kubejs_generated_pack_index (KubeJS 2001.6.5-build.26, LGPL-3.0; both sides). Ours; no KubeJS
 * code is carried.
 *
 * What KubeJS does. GeneratedResourcePack.listResources(type, namespace, path, visitor) walks EVERY generated entry (every
 * file under kubejs/data or kubejs/assets plus everything scripts generated) for each listing a reload makes, testing
 * namespace.equals and path.startsWith; with thousands of files and a few hundred listed folders per reload that is
 * millions of string tests, on both sides.
 *
 * What the switch does. For each generated map (by identity: KubeJS builds a new one after close()), an index per
 * namespace: the paths sorted, each with the position of its entry in the map's own iteration order. A listing finds the
 * prefix range by binary search and visits the matched entries in their original iteration order.
 *
 * Why the result is identical. Paths that start with a prefix form one contiguous range of the sorted paths; visiting the
 * matches sorted by their iteration position gives exactly the entries the original loop visits, in its order, with the
 * same key and value objects. The generated map is immutable (Map.copyOf), so its iteration order, recorded once, holds
 * for every later walk; getGenerated() is still called once per listing, as before.
 *
 * -Dbons_and_furious.kubejsGeneratedPackIndex=false switches it off at run time.
 * -Dbons_and_furious.kubejsGeneratedPackIndex.shadow=true: every listing is also walked the original way and the two visit
 * sequences compared (SHADOW_CHECKS / SHADOW_MISMATCHES, WARN for the first 20).
 */
public final class GeneratedPackIndex {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.kubejsGeneratedPackIndex", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.kubejsGeneratedPackIndex.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    private static volatile boolean announced;

    private GeneratedPackIndex() {}

    /** One namespace: its paths sorted, and for each the entry's position in the map's iteration order. */
    static final class Namespace {
        final String[] paths;
        final int[] positions;

        Namespace(String[] paths, int[] positions) {
            this.paths = paths;
            this.positions = positions;
        }
    }

    /** The index of one generated map (identity); entries in the map's iteration order. */
    public static final class Index {
        final Map<?, ?> map;
        final Object[] keys, values;
        final Map<String, Namespace> namespaces;

        Index(Map<?, ?> map, Object[] keys, Object[] values, Map<String, Namespace> namespaces) {
            this.map = map;
            this.keys = keys;
            this.values = values;
            this.namespaces = namespaces;
        }

        public boolean of(Map<?, ?> m) {
            return this.map == m;
        }
    }

    public static Index build(Map<ResourceLocation, ?> map) {
        int n = map.size();
        Object[] keys = new Object[n], values = new Object[n];
        Map<String, List<Object[]>> grouped = new HashMap<>();
        int i = 0;
        for (Map.Entry<ResourceLocation, ?> e : map.entrySet()) {
            keys[i] = e.getKey();
            values[i] = e.getValue();
            grouped.computeIfAbsent(e.getKey().m_135827_(), k -> new ArrayList<>()).add(new Object[] {e.getKey().m_135815_(), i});
            i++;
        }
        Map<String, Namespace> namespaces = new HashMap<>();
        for (Map.Entry<String, List<Object[]>> g : grouped.entrySet()) {
            List<Object[]> list = g.getValue();
            list.sort((a, b) -> ((String) a[0]).compareTo((String) b[0]));
            String[] paths = new String[list.size()];
            int[] positions = new int[list.size()];
            for (int k = 0; k < paths.length; k++) {
                paths[k] = (String) list.get(k)[0];
                positions[k] = (Integer) list.get(k)[1];
            }
            namespaces.put(g.getKey(), new Namespace(paths, positions));
        }
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: kubejs_generated_pack_index applies (KubeJS's generated pack lists folders from a sorted index){}",
                    SHADOW ? " - shadow verification on" : "");
        }
        return new Index(map, keys, values, namespaces);
    }

    /** Visits what the original loop visits for (namespace, prefix), in its order. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void visit(Index index, String namespace, String prefix, PackResources.ResourceOutput visitor) {
        Namespace ns = index.namespaces.get(namespace);
        if (ns == null) return;
        String[] paths = ns.paths;
        int lo = Arrays.binarySearch(paths, prefix);
        if (lo < 0) lo = -lo - 1;
        int hi = lo;
        while (hi < paths.length && paths[hi].startsWith(prefix)) hi++;
        if (hi == lo) return;
        int[] selected = Arrays.copyOfRange(ns.positions, lo, hi);
        if (selected.length > 1) Arrays.sort(selected);
        for (int p : selected) visitor.accept((ResourceLocation) index.keys[p], (IoSupplier) index.values[p]);
    }

    /** Shadow mode: the original loop's visit sequence against the index's. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void shadow(Map<ResourceLocation, ?> map, Index index, String namespace, String prefix) {
        SHADOW_CHECKS.incrementAndGet();
        List<Object> stock = new ArrayList<>(), ours = new ArrayList<>();
        for (Map.Entry<ResourceLocation, ?> r : map.entrySet())
            if (r.getKey().m_135827_().equals(namespace) && r.getKey().m_135815_().startsWith(prefix)) {
                stock.add(r.getKey());
                stock.add(r.getValue());
            }
        visit(index, namespace, prefix, (id, value) -> {
            ours.add(id);
            ours.add(value);
        });
        boolean same = stock.size() == ours.size();
        for (int i = 0; same && i < stock.size(); i++) same = stock.get(i) == ours.get(i);
        if (!same) {
            long m = SHADOW_MISMATCHES.incrementAndGet();
            if (m <= 20) LOGGER.warn("Bons and Furious: kubejs_generated_pack_index shadow mismatch #{} for {}:{}: {} vs {} visits", m, namespace, prefix, stock.size() / 2, ours.size() / 2);
        }
    }
}
