package bons.furious.patch.legendaryspellbooks_coo49;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious, the client switch of group legendaryspellbooks_coo49 on Legendary Spellbooks 0.3.2 for Minecraft 1.21.1
 * (LGPL-3.0; needs Iron's Spellbooks). Ours; no Legendary Spellbooks or Iron's Spellbooks code is carried. Loaded only on a
 * client (its mixin is in the client list).
 *
 * FlamebornDriftSpellHandler.onPlayerTick listens to NeoForge's PlayerTickEvent.Post, which the client fires for every
 * player it ticks (the local player and every other player it holds): on a client level it asks for every LivingEntity
 * within 64 blocks of that player (ClientLevel.getEntities(null, box, instanceof LivingEntity): a walk over every entity
 * section of a 128-block box) and, for each one that is spin-attacking (isAutoSpinAttack) with Iron's synced spin type equal
 * to its FLAMEBORN type, spawns the drift particles (drawing on that entity's random).
 *
 * legendary_spellbooks_drift_local_player_only (kind change, a gameplay fix, off by default; idea: Collections Of
 * Optimizations, legendaryspellbooks.localPlayerDriftScan, idea text only). otherPlayer(): the handler returns at once for
 * every client player but Minecraft's local player: one sweep per client tick around the local player instead of one per
 * ticked player, and a drifting caster's particles are spawned once per tick instead of once per nearby player (vanilla
 * drops particles farther than 32 blocks from the camera, inside the 64-block sweep). Server-side players (the integrated
 * server) are left to the handler, which does nothing for them. Flag:
 * -Dbons_and_furious.legendarySpellbooksDriftLocalPlayerOnly=false.
 */
public final class LsDriftClient {
    public static final String LOCAL_KEY = "legendary_spellbooks_drift_local_player_only";

    public static volatile boolean localEnabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.legendarySpellbooksDriftLocalPlayerOnly", "true"));
    /** For probes and harnesses (plain field): player ticks left out. */
    public static long leftOut;

    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    private static volatile boolean announced;

    private LsDriftClient() {
    }

    /** LsDriftLocalOnlyMixin: true when the handler's player is a client player other than Minecraft's local player. */
    public static boolean otherPlayer(Player player) {
        if (!localEnabled || player == null || !player.level().isClientSide()) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || player == mc.player) return false;
        leftOut++;
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: {} applies (the Flameborn Drift sweep runs for your own player only)", LOCAL_KEY);
        }
        return true;
    }
}
