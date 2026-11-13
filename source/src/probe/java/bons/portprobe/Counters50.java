package bons.portprobe;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * Development-only (never shipped), 1.0.50 port: the counters of the helpers of the switches this port adds, read when the
 * probe writes its result (the summary lines the helpers print at JVM exit may not reach the log once logging has shut
 * down). Every static LongAdder and boolean field of each helper is read by reflection (the booleans say which of its
 * flags were on), plus OutsideColumns.summary(). A helper whose class cannot be loaded (its target mod is absent) is
 * reported as unavailable. Reading a helper initializes it; a helper whose switch never ran has every counter at 0.
 */
final class Counters50 {
    private Counters50() {
    }

    /** {side, helper class}: "client" helpers are read by the client probe only. */
    private static final String[][] HELPERS = {
            {"both", "bons.furious.patch.flat_cache_columns.OutsideColumns"},
            {"both", "bons.furious.patch.climate_sampler_reuse.SamplerReuse"},
            {"both", "bons.furious.patch.endertech_start_reads.StartReads"},
            {"client", "bons.furious.patch.embeddium_hidden_blocks.HiddenBlocks"},
    };

    static Map<String, Object> read(boolean client) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (String[] h : HELPERS) {
            if (h[0].equals("client") && !client) continue;
            Map<String, Object> row = new TreeMap<>();
            try {
                Class<?> type = Class.forName(h[1], true, Counters50.class.getClassLoader());
                for (Field f : type.getDeclaredFields()) {
                    if (!Modifier.isStatic(f.getModifiers())) continue;
                    if (f.getType() != LongAdder.class && f.getType() != boolean.class) continue;
                    try {
                        f.setAccessible(true);
                        Object v = f.get(null);
                        row.put(f.getName(), v instanceof LongAdder a ? (Object) a.sum() : v);
                    } catch (Throwable t) {
                        row.put(f.getName(), "unreadable: " + t);
                    }
                }
                try {
                    Method summary = type.getMethod("summary");
                    row.put("summary", String.valueOf(summary.invoke(null)));
                } catch (NoSuchMethodException ignored) {
                }
            } catch (Throwable t) {
                row.put("unavailable", t.toString());
            }
            out.put(h[1].substring(h[1].lastIndexOf('.') + 1), row);
        }
        System.out.println("BONS_PORT_COUNTERS50 " + out);
        return out;
    }
}
