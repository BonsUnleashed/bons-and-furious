package bons.furious.patch.mekaweapons_tana_coo49;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch mekaweapons_tana_sweep_full_swing (Mekanism Weapons 4.2.1, server). Gameplay fix, OFF by default.
 *
 * The Meka-Tana's sweep (MekaWeapons.MekaWeaponsAttackEvent, an AttackEntityEvent listener that runs before Player.attack
 * reads or resets the attack strength) hits every living entity around the target with the full weapon damage on every
 * click, charged or not. With the switch on, the sweep needs a charged swing, vanilla's own sweep rule:
 * player.getAttackStrengthScale(0.5f) > 0.9f. The hit on the target itself is vanilla's and unchanged; with the sweeping
 * unit off nothing changes. Tested build 4.2.1 (the listener does not exist in 3.0, whose fingerprints do not match: the
 * switch stays off there). Runtime flag -Dbons_and_furious.mekaweaponsTanaSweepFullSwing=false. Mekanism Weapons is MIT; no
 * code of it is carried.
 */
public final class TanaSweep {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.mekaweaponsTanaSweepFullSwing", "true"));
    private static volatile boolean announced;

    private TanaSweep() {
    }

    /** False for a spam click: the sweep loop is skipped. */
    public static boolean charged(float attackStrengthScale) {
        if (attackStrengthScale > 0.9f) return true;
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: mekaweapons_tana_sweep_full_swing: the Meka-Tana sweeps only on charged swings");
        }
        return false;
    }
}
