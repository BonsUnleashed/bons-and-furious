package bons.portprobe;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * Development-only (never shipped), 1.1.0+mc1.21.1 port: what the helpers of the switches and fixes this port adds counted, read
 * when the probe writes its result (the summary lines the helpers print at JVM exit may not reach the log). Every static
 * LongAdder, AtomicLong, AtomicInteger, boolean, int and long field of each helper is read by reflection; the object fields named
 * in OBJECTS are reported as "null" or "set" (StartReads.window: the start window must be gone once a server has started, bug
 * sweep 5 F49). On the client also Guards.untouched("embeddium_draw_batch_cache") (bug sweep 5 F44: "" = Embeddium's
 * fillCommandBuffer is as shipped after every mod's mixins; anything else = why the batch cache stands down). A helper whose class
 * cannot be loaded is reported as unavailable. Reading a helper initializes it; a helper that never ran has every counter at 0.
 */
final class Counters110 {
    private Counters110() {
    }

    /** {side, helper class}: "client" helpers are read by the client probe only. */
    private static final String[][] HELPERS = {
            {"both", "bons.furious.patch.vanilla_zero_term.ZeroTerms"},
            {"both", "bons.furious.patch.formations_biome_first.BiomeFirst"},
            {"both", "bons.furious.patch.distanthorizons_light_check.LightCheck"},
            {"both", "bons.furious.patch.endertech_start_reads.StartReads"},
            {"client", "bons.furious.patch.embeddium_draw.DrawBatchCache"},
            {"client", "bons.furious.patch.embeddium_hidden_blocks.HiddenBlocks"},
    };
    private static final Set<String> OBJECTS = Set.of("window");

    static Map<String, Object> read(boolean client) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (String[] h : HELPERS) {
            if (h[0].equals("client") && !client) continue;
            Map<String, Object> row = new TreeMap<>();
            try {
                Class<?> type = Class.forName(h[1], true, Counters110.class.getClassLoader());
                for (Field f : type.getDeclaredFields()) {
                    if (!Modifier.isStatic(f.getModifiers())) continue;
                    Class<?> t = f.getType();
                    boolean number = t == LongAdder.class || t == AtomicLong.class || t == AtomicInteger.class || t == boolean.class || t == int.class
                            || t == long.class;
                    if (!number && !OBJECTS.contains(f.getName())) continue;
                    try {
                        f.setAccessible(true);
                        Object v = f.get(null);
                        if (OBJECTS.contains(f.getName()) && !number) row.put(f.getName(), v == null ? "null" : "set");
                        else if (v instanceof LongAdder a) row.put(f.getName(), a.sum());
                        else if (v instanceof AtomicLong a) row.put(f.getName(), a.get());
                        else if (v instanceof AtomicInteger a) row.put(f.getName(), a.get());
                        else row.put(f.getName(), v);
                    } catch (Throwable e) {
                        row.put(f.getName(), "unreadable: " + e);
                    }
                }
            } catch (Throwable e) {
                row.put("unavailable", e.toString());
            }
            out.put(h[1].substring(h[1].lastIndexOf('.') + 1), row);
        }
        if (client) {
            try {
                Object why = Class.forName("bons.furious.guard.Guards").getMethod("untouched", String.class).invoke(null, "embeddium_draw_batch_cache");
                out.put("untouched_embeddium_draw_batch_cache", why == null ? "never checked" : why);
            } catch (Throwable e) {
                out.put("untouched_embeddium_draw_batch_cache", "unavailable: " + e);
            }
        }
        System.out.println("BONS_PORT_COUNTERS110 " + out);
        return out;
    }
}
