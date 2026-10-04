package bons.portprobe;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Development-only (never shipped): reads every shadow helper listed in /probe-shadow-helpers.txt
 * ("client <class>" = client-only helper, "both <class>" = either side). A helper exposes
 * {@code public static final boolean SHADOW} and {@code AtomicLong SHADOW_CHECKS, SHADOW_MISMATCHES}: with its
 * {@code bons_and_furious.<name>.shadow} property set it computes the original answer next to ours and counts
 * disagreements. Any mismatch fails the probe; a helper whose shadow was on but saw no call is reported as idle.
 */
final class ShadowHarvest {
    private ShadowHarvest() {}

    static Map<String, Object> harvest(boolean client) {
        Map<String, Object> out = new TreeMap<>();
        List<String> mismatching = new ArrayList<>(), idle = new ArrayList<>(), unavailable = new ArrayList<>();
        long checks = 0, mismatches = 0;
        try (var in = ShadowHarvest.class.getResourceAsStream("/probe-shadow-helpers.txt")) {
            if (in == null) { out.put("helpers", Map.of()); return out; }
            for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\\R")) {
                line = line.strip();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] parts = line.split("\\s+");
                String side = parts[0], name = parts[1];
                if (side.equals("client") && !client) continue;
                Map<String, Object> row = new LinkedHashMap<>();
                try {
                    Class<?> type = Class.forName(name, true, ShadowHarvest.class.getClassLoader());
                    boolean shadow = type.getField("SHADOW").getBoolean(null);
                    long c = ((AtomicLong) type.getField("SHADOW_CHECKS").get(null)).get();
                    long m = ((AtomicLong) type.getField("SHADOW_MISMATCHES").get(null)).get();
                    row.put("shadow", shadow);
                    row.put("checks", c);
                    row.put("mismatches", m);
                    checks += c;
                    mismatches += m;
                    if (m != 0) mismatching.add(name);
                    if (shadow && c == 0) idle.add(name);
                } catch (Throwable t) {
                    row.put("unavailable", t.toString());
                    unavailable.add(name);
                }
                out.put(name, row);
            }
        } catch (Exception e) {
            out.put("error", e.toString());
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("checks", checks);
        summary.put("mismatches", mismatches);
        summary.put("mismatching", mismatching);
        summary.put("idle", idle);
        summary.put("unavailable", unavailable);
        out.put("_summary", summary);
        System.out.println("BONS_PORT_SHADOW checks=" + checks + " mismatches=" + mismatches + " mismatching=" + mismatching + " idle=" + idle);
        return out;
    }

    static boolean clean(Map<String, Object> harvest) {
        Object s = harvest.get("_summary");
        return s instanceof Map<?, ?> m && Long.valueOf(0).equals(m.get("mismatches"));
    }
}
