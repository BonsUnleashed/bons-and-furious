package bons.furious.patch.bomd_c49;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraftforge.event.TickEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch bomd_player_tick_end_only (Bosses of Mass Destruction 1.1.2, server, since 1.0.49). Gameplay fix,
 * off by default. Idea: Collections Of Optimizations (bmd.singlePlayerTickPhase), idea text only. Our own logic (LGPL target,
 * no code carried).
 *
 * ForgeEvents.onPlayerTick has no phase test, so it runs twice per player per server tick (PlayerTickEvent START and END):
 * the Lich's ten-sample movement history of each player gets two samples per tick (it covers five ticks instead of ten),
 * and LevitationBlockEntity.tickFlight runs twice.
 *
 * What changes (switch on): the START post returns before the handler; the END post runs it unchanged. One sample per
 * tick, one tickFlight per tick; nothing else differs. Client posts were already ignored by the handler itself.
 *
 * -Dbons_and_furious.bomdPlayerTickEndOnly=false runs the handler on both phases (stock) even with the switch on.
 */
public final class PlayerTickPhase {
    public static final String KEY = "bomd_player_tick_end_only";
    public static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.bomdPlayerTickEndOnly", "true"));
    private static volatile boolean announced;

    private PlayerTickPhase() {
    }

    public static void onPlayerTick(TickEvent.PlayerTickEvent event, Operation<Void> original) {
        if (enabled && event.phase == TickEvent.Phase.START) {
            if (!announced) {
                announced = true;
                LOGGER.info("Bons and Furious: {} applies (Bosses of Mass Destruction's player tick handler runs once per tick, at the end)", KEY);
            }
            return;
        }
        original.call(event);
    }
}
