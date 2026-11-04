package bons.furious.patch.moreartifacts_coo49;

import net.minecraftforge.event.TickEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch moreartifacts_equip_tick_end_only (More Artifacts 1.5.5, All Rights Reserved; both sides; kind
 * change, off by default). Ours; no More Artifacts code is carried. Idea: Collections Of Optimizations
 * (moreartifacts.tickEndPhaseOnly), idea text only.
 *
 * <p>DamageEvents.onTick has no phase check: it compares the player's equipped artifacts with the kept ones and recomputes
 * the damage reduction / increase totals on change, at the START and at the END of every player tick, on both sides. With
 * the switch on it runs at the END only. Trade-off: an artifact equipped or taken off between two ticks counts from the
 * end of the player's next tick instead of its start, so damage the player takes during its own tick (fire, lava, fall,
 * drowning, poison) right after the change still uses the previous totals for that one tick. The totals after every END
 * phase are the same as before. Runtime flag: -Dbons_and_furious.moreartifactsEquipTickEndOnly=false (the config switch
 * decides; the flag is the emergency off).
 */
public final class MaTickEnd {
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.moreartifactsEquipTickEndOnly", "true"));
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    private static volatile boolean announced;

    private MaTickEnd() {
    }

    /** True for the START phase while the switch is on: the handler then returns at once. */
    public static boolean skip(TickEvent.PlayerTickEvent event) {
        if (!enabled || event.phase != TickEvent.Phase.START) return false;
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: moreartifacts_equip_tick_end_only applies (More Artifacts' equip check runs at the end of each player tick only)");
        }
        return true;
    }
}
