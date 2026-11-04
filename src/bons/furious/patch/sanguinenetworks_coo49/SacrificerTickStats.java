package bons.furious.patch.sanguinenetworks_coo49;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraftforge.fml.ModList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switches sanguinenetworks_tick_stats_reuse (Sanguine Neural Networks 2.0.1, package com.luna) and
 * sanguinenetworks_leo_tick_stats_reuse (1.2.1, package com.leo), since 1.0.49, server side (both exact, on by default:
 * against the mod's own class a running sacrificer's tick is faster; an idle one runs no code of ours). Helper of
 * SacrificerTickStatsMixin / LeoSacrificerTickStatsMixin; no Sanguine Neural Networks code is carried.
 *
 * What the mod does. The Virtual Sacrificer's tick works out what the data model in slot 0 is worth
 * (getModelFromStack: blood per cycle and energy per tick, from the sacrifice recipe list, the entity registry and Hostile
 * Neural Networks' model registry: a list copy, a registry lookup and a model comparison per recipe) once at the start of the
 * tick (2.0.1) or up to three times (1.2.1), and then again inside getRFTick() twice: once to check the energy buffer and
 * once to take the energy out.
 *
 * What the switches do (exact). Inside tick() only, the later questions are answered with the stats the same tick worked
 * out first: getRFTick() returns that Pair's energy, 1.2.1's second and third getModelFromStack return that Pair. 2.0.1:
 * the first getRFTick hook works the stats out with the lookup the mod's getRFTick does and keeps them, the second
 * answers from them; nothing of ours runs before the first energy question, so a tick without a model runs the mod's code
 * alone (2.0.1's own first lookup, which every tick does, is left as it is). 1.2.1 uses its first answer inline, so a value
 * hook keeps it with the slot-0 stack object it is for (a tick with an empty model slot reaches none of these calls).
 * The later questions reuse the kept stats for that same object only. Between the first question and the last one,
 * tick() changes only the catalyst slot
 * (slot 1, shrunk in place), the progress and catalyst fields, the energy buffer and the Blood Magic altar; nothing it calls
 * writes slot 0, its NBT, the recipe table or a registry (bytecode census in the harness). getModelFromStack itself only
 * reads (1.2.1 creates the stack's empty "data_model" compound on its first call when it is missing, which the first call
 * keeps doing; later calls would find it). So every later question would answer the same numbers. Not reused: another
 * stack object in slot 0, a subclass of the block entity, and in 2.0.1 a pack with Extra Hostile Networks (mod id
 * extrahnn) loaded, whose combined models answer through that mod's own code. The client's screen and tooltip questions
 * are left as they are.
 *
 * Runtime flags bons_and_furious.sanguinenetworksTickStatsReuse / ...LeoTickStatsReuse. SHADOW MODE (verification only):
 * -Dbons_and_furious.sanguinenetworksTickStatsReuse.shadow=true (and ...LeoTickStatsReuse.shadow) also asks the mod at
 * every reused question, answers with the mod's answer and compares (SHADOW_CHECKS, SHADOW_MISMATCHES, WARN for the first
 * 20).
 */
public final class SacrificerTickStats {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.sanguinenetworksTickStatsReuse", "true"));
    public static volatile boolean leoEnabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.sanguinenetworksLeoTickStatsReuse", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.sanguinenetworksTickStatsReuse.shadow");
    public static final boolean LEO_SHADOW = Boolean.getBoolean("bons_and_furious.sanguinenetworksLeoTickStatsReuse.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    /** Statistics for probes and the harness (plain field): questions answered from the tick's own stats. */
    public static long reused;
    private static volatile boolean announced, announcedLeo;

    private SacrificerTickStats() {
    }

    private static final class Extra {
        static final boolean ABSENT = absent();

        private static boolean absent() {
            try {
                ModList m = ModList.get();
                return m == null || !m.isLoaded("extrahnn");
            } catch (Throwable t) {
                return false;
            }
        }
    }

    /** 2.0.1 only: Extra Hostile Networks is not loaded, so getModelFromStack answers from Hostile Neural Networks' data alone. */
    public static boolean plainModels() {
        return Extra.ABSENT;
    }

    /** One question answered from the tick's own stats. */
    public static void reusedOne(boolean leo) {
        reused++;
        if (leo ? !announcedLeo : !announced) {
            if (leo) announcedLeo = true;
            else announced = true;
            LOGGER.info("Bons and Furious: {} applies (the Virtual Sacrificer works out its data model's stats once per tick)",
                    leo ? "sanguinenetworks_leo_tick_stats_reuse" : "sanguinenetworks_tick_stats_reuse");
        }
    }

    /** Shadow mode: the kept answer against the mod's; returns the mod's. */
    public static int shadow(String key, int kept, int fresh) {
        SHADOW_CHECKS.incrementAndGet();
        if (kept != fresh) mismatch(key, kept, fresh);
        return fresh;
    }

    /** Shadow mode for a whole Pair (1.2.1's repeated getModelFromStack): compares both values; returns the mod's. */
    public static <P> P shadowPair(String key, Object keptFirst, Object keptSecond, Object freshFirst, Object freshSecond, P fresh) {
        SHADOW_CHECKS.incrementAndGet();
        if (!Objects.equals(keptFirst, freshFirst) || !Objects.equals(keptSecond, freshSecond)) mismatch(key, keptFirst + "/" + keptSecond, freshFirst + "/" + freshSecond);
        return fresh;
    }

    private static void mismatch(String key, Object kept, Object fresh) {
        long m = SHADOW_MISMATCHES.incrementAndGet();
        if (m <= 20) LOGGER.warn("Bons and Furious: {} shadow mismatch #{}: kept {} but the mod answered {}", key, m, kept, fresh);
    }
}
