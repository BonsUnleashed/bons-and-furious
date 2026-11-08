package bons.furious.patch.powah_coo49;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious 1.0.49 switch powah_zero_energy_push_skip (Powah 6.2.10, server; kind change, off by default; helper of
 * PowahEnergyPushMixin and PowahStoragePushMixin). No Powah code is carried.
 *
 * What Powah does. Every Powah block that holds energy ends its tick by offering min(transfer rate, stored) out of each face
 * it can extract from, also when that is 0 (a panel at night, a cell that is empty): cells, panels, generators, ender blocks
 * and dischargers in AbstractEnergyStorage.extractFromSides, through a NeoForge BlockCapabilityCache per face (the
 * neighbour's energy capability, looked up again only after the neighbour changed) and receiveEnergy(amount, false); the
 * reactor in its postTick through EnergyUtil.pushEnergy, which looks the neighbour's capability up on every call
 * (level.getCapability, which also creates a missing neighbour block entity) and hands it receiveEnergy(amount, false).
 *
 * What the switch changes (trade-off, off by default). An offer of 0 or less answers 0 at once: the neighbour is not handed
 * receiveEnergy(0, false), which NeoForge's energy contract defines as moving nothing but which a receiver may still act on
 * (per-tick input statistics; Powah's own cables move their round-robin start on every offer), and pushEnergy does not look
 * the neighbour up for it. The face then extracts 0 from the block as before. Offers above 0 run as before. Runtime flag
 * bons_and_furious.powahZeroEnergyPushSkip.
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
