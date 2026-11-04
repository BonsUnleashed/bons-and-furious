package bons.furious.patch.summonity_coo49;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.world.entity.Entity;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch summonity_dragon_parts_memo (Summonity 0.1.7.1, server side; kind change, off by default). Ours;
 * no Summonity code is carried. Idea: Collections Of Optimizations (summonity.cacheDragonParts), idea text only.
 *
 * The Stardust Dragon head asks getDragonParts (a search of every Stardust Dragon entity in its box grown by 64 blocks,
 * filtered to its own parts) when it moves its body, when it attacks and when it repairs its chain. With the switch on the
 * chain repair's search is kept on the head (SummonityDragonPartsMixin) and the moving / attacking asks reuse it while it
 * is younger than KEEP_TICKS (5) and every kept part is still in the world; a part the head spawns drops it. Trade-off:
 * for up to 4 ticks a part that leaves the 64-block box or a part added by other means (chunk loading) is seen later, and
 * the parts keep their list order (the order of the damage calls of one attack pass). Flag:
 * -Dbons_and_furious.summonityDragonPartsMemo=false.
 */
public final class DragonParts {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.summonityDragonPartsMemo", "true"));
    /** How long a kept part list is used (COO's window: 5 ticks). */
    public static final int KEEP_TICKS = 5;
    public static final AtomicLong REUSES = new AtomicLong();
    private static volatile boolean announced;

    private DragonParts() {
    }

    /** True when the kept list may be handed out at game time {@code now}. */
    public static boolean usable(List<?> kept, long at, long now) {
        if (kept == null || now < at || now - at >= KEEP_TICKS) return false;
        for (Object part : kept) if (part instanceof Entity e && e.m_213877_()) return false;
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: summonity_dragon_parts_memo applies (a Stardust Dragon head reuses its part list for up to {} ticks)", KEEP_TICKS);
        }
        return true;
    }
}
