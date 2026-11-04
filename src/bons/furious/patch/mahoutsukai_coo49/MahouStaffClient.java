package bons.furious.patch.mahoutsukai_coo49;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch mahoutsukai_staff_sensitivity_local_only (Mahou Tsukai 1.20.1-v1.34.81, All Rights Reserved;
 * client; kind change: a gameplay fix, off by default). Ours; no Mahou Tsukai code is carried. Idea: Collections Of
 * Optimizations (mahoutsukai.fixStaffSensitivity), idea text only. Loaded only on a client (its mixin is in the client list).
 *
 * ServerHandler.tickCheck ends with "every 100 ticks of tickCounter, if the ticked player is not using a Mystic Staff,
 * proxy.speedUp()"; on the client ClientProxy.speedUp puts back the LOCAL mouse sensitivity the staff lowered while it
 * channels. tickCounter is advanced only by worldTick on server levels, so a client connected to a dedicated server sees 0
 * for ever and runs the check on every player tick, for every player it ticks: any other player nearby who is not using a
 * staff resets your channelling slowdown at once. With the switch, on a client level the call is made only when the ticked
 * player is the local player (Minecraft.player); calls on server levels (the integrated server's thread) are unchanged, so
 * single player behaves as before. Runtime flag -Dbons_and_furious.mahoutsukaiStaffSensitivityLocalOnly=false.
 */
public final class MahouStaffClient {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.mahoutsukaiStaffSensitivityLocalOnly", "true"));
    /** For harnesses (plain field): speedUp calls left out (another player on a client level). */
    public static long leftOut;
    private static volatile boolean announced;

    private MahouStaffClient() {
    }

    /** MahouStaffSensitivityMixin (@WrapWithCondition on the speedUp call): true = make the call as shipped. */
    public static boolean speedUp(TickEvent.PlayerTickEvent event) {
        if (!enabled || event == null) return true;
        Player player = event.player;
        if (player == null) return true;
        Level level = player.m_9236_();
        if (level == null || !level.f_46443_) return true;
        Minecraft mc = Minecraft.m_91087_();
        if (mc == null || player == mc.f_91074_) return true;
        leftOut++;
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: mahoutsukai_staff_sensitivity_local_only applies (other players no longer reset your Mystic Staff mouse sensitivity)");
        }
        return false;
    }
}
