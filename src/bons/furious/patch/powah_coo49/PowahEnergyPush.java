package bons.furious.patch.powah_coo49;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious 1.0.49 switch powah_zero_energy_push_skip (Powah 5.0.11, server; kind change, off by default; helper of
 * PowahEnergyPushMixin). No Powah code is carried.
 *
 * What Powah does. Every Powah block that holds energy (cells, panels, generators, ender cells, dischargers, hoppers, the
 * reactor) ends its tick by pushing min(transfer rate, stored) out of each face it can extract from, through
 * ForgeEnvHandler.pushEnergy: level.getBlockEntity(neighbour) (which creates a missing neighbour block entity), its energy
 * capability, and receiveEnergy(amount, false), also when the amount is 0 (a panel at night, a cell that is empty).
 *
 * What the switch changes (trade-off, off by default). An offer of 0 or less answers 0 at once: the neighbour's block entity
 * is not looked up (so a missing one is not created by this call) and the neighbour is not handed receiveEnergy(0, false),
 * which Forge's energy contract defines as moving nothing but which a foreign receiver may still record (per-tick input
 * statistics). Offers above 0 run as before. Runtime flag bons_and_furious.powahZeroEnergyPushSkip.
 */
public final class PowahEnergyPush {
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.powahZeroEnergyPushSkip", "true"));
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    private static volatile boolean announced;

    private PowahEnergyPush() {
    }

    /** True when the push offers nothing and the switch answers 0 for it. */
    public static boolean idle(long howMuch) {
        if (!enabled || howMuch > 0L) return false;
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: powah_zero_energy_push_skip applies (Powah blocks with nothing to give no longer ask their neighbours to take 0)");
        }
        return true;
    }
}
