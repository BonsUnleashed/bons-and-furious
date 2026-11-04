package bons.furious.patch.brutalbosses_coo49;

import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.client.Minecraft;
import net.minecraftforge.event.TickEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch brutalbosses_boss_bar_local_player (Brutal Bosses 8.5, All Rights Reserved; client; gameplay
 * fix, off by default). Ours; no Brutal Bosses code is carried. Idea: Collections Of Optimizations
 * (brutalbosses.leanBossUiTick), idea text only.
 *
 * Brutal Bosses' client boss bar bookkeeping (ClientEventHandler.onPlayerTick) listens to PlayerTickEvent without a phase
 * check, and the client fires that event at the start and the end of the tick of EVERY player entity it ticks (remote
 * players too). Every fifth game tick each of those runs updates the bars (idempotent within a tick), drops dead or timed
 * out bosses, and casts a line-of-sight ray from THAT player to every boss with a bar, renewing the bar's 30 second
 * timeout when it sees the boss. With 10 players in view and one boss that is 22 runs and 22 rays where one would do.
 *
 * With the switch on only the local player's end-of-tick event runs it. Gameplay fix (off by default): another player
 * looking at a boss no longer keeps YOUR boss bar open: it closes 30 seconds after you last saw the boss yourself; the
 * line of sight is tested from your end-of-tick position only. Flag: -Dbons_and_furious.brutalbossesBossBarLocalPlayer=false.
 */
public final class BossBarLocal {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.brutalbossesBossBarLocalPlayer", "true"));
    /** Handler runs left out (for the harness). */
    public static final AtomicLong SKIPPED = new AtomicLong();
    private static volatile boolean announced;

    private BossBarLocal() {
    }

    public static boolean skip(TickEvent.PlayerTickEvent event) {
        if (!enabled) return false;
        if (event.phase == TickEvent.Phase.END && event.player == Minecraft.m_91087_().f_91074_) return false;
        SKIPPED.incrementAndGet();
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: brutalbosses_boss_bar_local_player applies (boss bars are kept by your own sight only; off by default)");
        }
        return true;
    }
}
