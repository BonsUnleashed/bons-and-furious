package bons.furious.patch.fdbosses_c2;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch fdbosses_phase_sphere_local_only (a FIX, client desync; FD Bosses 3.1.0.3, CLIENT). Helper of
 * PhaseSphereLocalOnlyMixin; no FD Bosses code here.
 *
 * FD Bosses' PlayerMixin calls PhaseSphereHandler.onChesedItemUse(player) from Player.tick for every player the game ticks,
 * and on the client that is every player in view, not only your own. For a player that is not a ServerPlayer the method
 * runs its client branch, which works on client-wide static state that belongs to the local player:
 * isUsingChesedItem (set only by the PhaseSphere packet for the local player, BossClientPackets.chesedItemUse) and
 * clientsideChesedItemUseTick (read only for the local player's held Phase Sphere, PhaseSphere's colour handler). So while
 * you used the Phase Sphere, every other player in view was given flying abilities, noPhysics and onGround = false on your
 * client, and the use timer advanced once per player in view per tick instead of once.
 * The fix: on a client level, the method now runs only for the local player (Minecraft.getInstance().player); for any
 * other player it does nothing. The local player, and every player of a server level (integrated server included), get
 * exactly the original method.
 *
 * Ported to 1.21.1 (FD Bosses 3.1.0.3 for 1.21.1): onChesedItemUse is unchanged; the counter's only reader moved from
 * PhaseSphere's colour lambda (initializeClient) to the same colour lambda registered through NeoForge's client extensions
 * (BossClientModEvents.lambda$registerClientExtensions$10: the local player's use item only), and isUsingChesedItem has
 * one more writer, BossClientEvents.deathEvent (client only: false when the local player dies). Both statics are still
 * local-player state, so the fix is the same.
 */
public final class PhaseSphereLocal {
    /** Runtime switch. -Dbons_and_furious.fdbossesPhaseSphereLocalOnly=false runs the original for every player. */
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.fdbossesPhaseSphereLocalOnly", "true"));
    /** Calls left out for players other than the local one (statistics for probes; client thread). */
    public static long skipped;
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    private static volatile boolean announced;

    private PhaseSphereLocal() {
    }

    /** True for a player of a client level that is not the local player: FD Bosses' client branch is not about them. */
    public static boolean otherClientPlayer(Player player) {
        Level level = player.level();
        if (level == null || !level.isClientSide || player == Minecraft.getInstance().player) return false;
        skipped++;
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: fdbosses_phase_sphere_local_only: the Phase Sphere's client-side effect applies to your own player only "
                    + "(FD Bosses applied it to every player in view)");
        }
        return true;
    }
}
