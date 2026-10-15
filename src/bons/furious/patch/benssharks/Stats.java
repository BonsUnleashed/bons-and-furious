package bons.furious.patch.benssharks;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.LongAdder;

/** Ben's Sharks switches: evidence counters (cheap LongAdders), logged at server stop and read by test rigs through {@link QA}. */
public final class Stats {
    private Stats() {}

    public static final LongAdder SHARK_TICKS = new LongAdder();
    public static final LongAdder PROC_CALLS = new LongAdder();
    public static final LongAdder ARMOR_CHECKS = new LongAdder();
    public static final LongAdder DOLPHIN_TICKS = new LongAdder();
    public static final LongAdder BLEED_PASSES = new LongAdder();
    public static final LongAdder SMOOTH_APPLIED = new LongAdder();
    public static final LongAdder TRIDENT_TICKS = new LongAdder();
    public static final LongAdder GOALS_MERGED = new LongAdder();
    public static final LongAdder GOALS_REPLACED = new LongAdder();
    public static final LongAdder MERGED_SEARCHES = new LongAdder();

    public static Map<String, Long> snapshot() {
        Map<String, Long> m = new LinkedHashMap<>();
        m.put("shark_ticks", SHARK_TICKS.sum());
        m.put("proc_calls", PROC_CALLS.sum());
        m.put("armor_checks", ARMOR_CHECKS.sum());
        m.put("dolphin_ticks", DOLPHIN_TICKS.sum());
        m.put("bleed_passes", BLEED_PASSES.sum());
        m.put("smooth_applied", SMOOTH_APPLIED.sum());
        m.put("trident_ticks", TRIDENT_TICKS.sum());
        m.put("goals_merged_mobs", GOALS_MERGED.sum());
        m.put("goals_replaced", GOALS_REPLACED.sum());
        m.put("merged_searches", MERGED_SEARCHES.sum());
        return m;
    }
}
