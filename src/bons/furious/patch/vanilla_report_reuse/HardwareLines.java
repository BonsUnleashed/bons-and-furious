package bons.furious.patch.vanilla_report_reuse;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.SystemReport;
import org.slf4j.Logger;

/**
 * Bons and Furious switch vanilla_system_report_hardware_reuse (Minecraft 1.20.1, both sides). Kind: exact variant. No Mojang
 * code here.
 *
 * What vanilla does. Every SystemReport (each crash report builds one, and mods build crash reports for errors they handle
 * and log) asks the OSHI hardware library for the processor, the graphics cards and the memory modules again: on Windows
 * these are WMI and registry queries that take a few hundred milliseconds together. They are written as detail lines
 * through SystemReport.ignoreErrors (m_143516_) groups "processor", "graphics" and "physical memory".
 *
 * What the switch does. The first time one of those three groups completes without an error, the detail lines it added
 * (key and text, in order) are kept; later reports write the same lines through the same setDetail (m_143519_), in the same
 * order and at the same place, without running the group. Every other line is read live as before (memory in use,
 * virtual and swap memory, JVM flags, CPU count, every line other mods add). A group that throws, or that wrote an "ERR"
 * line (a single query failed), is not kept and runs again next time, as in vanilla. Exact variant: the three groups'
 * lines of later reports are copies of the first successful report's lines (the hardware does not change while the game
 * runs; nothing else differs).
 *
 * -Dbons_and_furious.systemReportHardwareReuse=false switches it off at run time (later reports query the hardware again).
 * -Dbons_and_furious.systemReportHardwareReuse.shadow=true (verification runs): the groups always run; their lines are
 * compared with the kept copy (SHADOW_CHECKS / SHADOW_MISMATCHES, at most 20 WARN lines).
 */
public final class HardwareLines {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.systemReportHardwareReuse", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.systemReportHardwareReuse.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    /** Groups whose lines describe hardware that cannot change while the game runs. */
    private static final List<String> STATIC = List.of("processor", "graphics", "physical memory");
    private static final Map<String, List<String[]>> KEPT = new ConcurrentHashMap<>();
    private static volatile boolean announced;

    private HardwareLines() {
    }

    /** One ignoreErrors(group, body) call of SystemReport.putHardware / putMemory. */
    public static void group(SystemReport report, Map<String, String> details, String group, Runnable body, Operation<Void> original) {
        if (!enabled || !STATIC.contains(group)) {
            original.call(report, group, body);
            return;
        }
        List<String[]> kept = KEPT.get(group);
        if (kept != null && !SHADOW) {
            for (String[] line : kept) report.m_143519_(line[0], line[1]);
            if (!announced) {
                announced = true;
                LOGGER.info("Bons and Furious: vanilla_system_report_hardware_reuse applies (system reports reuse the processor, graphics and memory module lines)");
            }
            return;
        }
        List<Map.Entry<String, String>> before = new ArrayList<>(details.size());
        for (Map.Entry<String, String> e : details.entrySet()) before.add(Map.entry(e.getKey(), String.valueOf(e.getValue())));
        boolean[] ran = new boolean[1];
        original.call(report, group, (Runnable) () -> {
            body.run();
            ran[0] = true;
        });
        List<String[]> lines = added(details, before);
        if (lines == null || !ran[0]) return;
        if (kept == null) {
            for (String[] line : lines) if ("ERR".equals(line[1])) return;
            KEPT.putIfAbsent(group, List.copyOf(lines));
        } else {
            SHADOW_CHECKS.incrementAndGet();
            boolean same = kept.size() == lines.size();
            for (int i = 0; same && i < kept.size(); i++) same = kept.get(i)[0].equals(lines.get(i)[0]) && kept.get(i)[1].equals(lines.get(i)[1]);
            if (!same) {
                long m = SHADOW_MISMATCHES.incrementAndGet();
                if (m <= 20) LOGGER.warn("Bons and Furious: vanilla_system_report_hardware_reuse shadow mismatch #{} in group {}", m, group);
            }
        }
    }

    /** The lines the group appended (keys new to the map, in order), or null when it changed lines that were there before. */
    private static List<String[]> added(Map<String, String> details, List<Map.Entry<String, String>> before) {
        if (details.size() < before.size()) return null;
        List<String[]> out = new ArrayList<>();
        int i = 0;
        for (Map.Entry<String, String> e : details.entrySet()) {
            if (i < before.size()) {
                Map.Entry<String, String> b = before.get(i);
                if (!b.getKey().equals(e.getKey()) || !b.getValue().equals(String.valueOf(e.getValue()))) return null;   // keep nothing
            } else {
                out.add(new String[] {e.getKey(), e.getValue()});
            }
            i++;
        }
        return out;
    }

    /** For the proof harness: forget the kept lines. */
    public static void reset() {
        KEPT.clear();
    }

    /** For the proof harness: the kept groups. */
    public static int keptGroups() {
        return KEPT.size();
    }
}
