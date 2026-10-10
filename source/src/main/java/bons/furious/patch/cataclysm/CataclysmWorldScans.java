package bons.furious.patch.cataclysm;

import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Runtime switches and the shared tests of five small Bons and Furious switches on L_Ender's Cataclysm (since 1.0.36;
 * 1.21.1 tested build: L_Ender's Cataclysm 1.21.1-3.33, L_Ender's Cataclysm 1.21.1-3.33.jar, with Lionfish API 3.1,
 * lionfishapi-3.1.jar). Ours; no Cataclysm code (CC-BY-NC-ND-4.0). Each test only decides whether Cataclysm's unchanged
 * code would use a query result; the reasons are in the mixins' javadoc.
 *
 *  cataclysm_area_attack_scans  (both sides) sandstorm, ashen breath, earthquake and void rune scan for living entities
 *      every tick but can hurt them only on every 3rd / 5th tick: the scan is skipped on the other ticks.
 *  cataclysm_beam_client_scans  (client) the death laser and the mini abyss blast collect the entities on their beam on
 *      both sides but use the list only on the server.
 *  cataclysm_server_leg_solver  (server) Clawdian / Ancient Remnant leg solvers probe the ground every tick for the leg
 *      animation; only client model classes read the result.
 *  cataclysm_coral_swim_checks  (both sides) the Coral Golem / Coralssus swim checks run a collision query before reading
 *      the swim flag that already decides the answer on almost every tick.
 *  cataclysm_helm_scan          (both sides) the Monstrous Helm collects the entities around its wearer every tick but uses
 *      them only when it triggers.
 * Properties: -Dbons_and_furious.cataclysmAreaAttackScans / cataclysmBeamClientScans / cataclysmServerLegSolver /
 * cataclysmCoralSwimChecks / cataclysmHelmScan = false restores the stock behaviour of that switch.
 *
 * Ported to 1.21.1: the tests are unchanged. Cataclysm 3.33 dropped the Phantom Halberd's tickCount % 5 gate (its
 * damage() now scans and hurts on every tick of its window), so that attack is no longer covered; the Ancient Ancient
 * Remnant (old model) no longer exists, so the leg-solver switch covers the Clawdian and the Ancient Remnant.
 */
public final class CataclysmWorldScans {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean areaAttackScans = flag("cataclysmAreaAttackScans");
    public static volatile boolean beamClientScans = flag("cataclysmBeamClientScans");
    public static volatile boolean serverLegSolver = flag("cataclysmServerLegSolver");
    public static volatile boolean coralSwimChecks = flag("cataclysmCoralSwimChecks");
    public static volatile boolean helmScan = flag("cataclysmHelmScan");
    private static volatile int announced;

    private CataclysmWorldScans() {
    }

    private static boolean flag(String name) {
        return !"false".equalsIgnoreCase(System.getProperty("bons_and_furious." + name, "true"));
    }

    /** One INFO line per switch, the first time it acts. */
    public static void announce(int bit, String key, String what) {
        if ((announced & bit) != 0) return;
        synchronized (CataclysmWorldScans.class) {
            if ((announced & bit) != 0) return;
            announced |= bit;
        }
        LOGGER.info("Bons and Furious: {} applies ({})", key, what);
    }

    /**
     * True when an area attack that can only hurt on ticks where tickCount % every == 0 may skip this tick's scan: not
     * such a tick, and its caster lookup is settled (a known caster, no caster UUID, or not a server level: then the
     * getCaster() calls inside the skipped damage() calls could not have resolved and kept a caster).
     */
    public static boolean skipAreaScan(Entity attack, int every, LivingEntity caster, UUID casterUuid) {
        if (!areaAttackScans || attack.tickCount % every == 0) return false;
        if (caster == null && casterUuid != null && attack.level() instanceof ServerLevel) return false;
        announce(1, "cataclysm_area_attack_scans", "area attacks look for targets only on the ticks they can hurt");
        return true;
    }

    /** As skipAreaScan, for attacks whose damage code reads the caster field directly (no lookup). */
    public static boolean skipAreaScan(Entity attack, int every) {
        if (!areaAttackScans || attack.tickCount % every == 0) return false;
        announce(1, "cataclysm_area_attack_scans", "area attacks look for targets only on the ticks they can hurt");
        return true;
    }

    public static boolean skipBeamScan(Level level) {
        if (!beamClientScans || !level.isClientSide) return false;
        announce(2, "cataclysm_beam_client_scans", "beams look for entities only on the server, where the list is used");
        return true;
    }

    public static boolean skipLegSolver(LivingEntity entity) {
        if (!serverLegSolver || entity.level().isClientSide) return false;
        announce(4, "cataclysm_server_leg_solver", "leg solvers run only on the client, the only reader");
        return true;
    }

    public static void coralSwimActs() {
        announce(8, "cataclysm_coral_swim_checks", "Coral Golem and Coralssus swim checks read the swim flag before the collision query");
    }

    public static void helmActs() {
        announce(16, "cataclysm_helm_scan", "the Monstrous Helm looks for entities only when it triggers");
    }
}
