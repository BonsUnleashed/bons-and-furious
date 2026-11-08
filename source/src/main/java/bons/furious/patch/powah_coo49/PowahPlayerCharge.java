package bons.furious.patch.powah_coo49;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious 1.0.49 switch powah_idle_player_charge_skip (Powah 6.2.10, server; kind change, off by default; helper of
 * PowahPlayerChargeMixin). No Powah code is carried.
 *
 * What Powah does. ChargeUtil.chargeItemsInPlayerInv (a Player Transmitter with a reachable bound player, and every charging
 * Battery in a player's inventory, every tick) copies the player's main inventory, armour and offhand stacks into a list,
 * posts a ChargeableItemsEvent on NeoForge.EVENT_BUS (other mods may add stacks), removes the stacks the filter rejects and
 * offers every remaining stack with an item energy capability receiveEnergy(min(maxPerSlot, maxTotal - charged), false).
 * With an empty transmitter or battery (maxTotal 0) or charge rate 0 every offer is 0.
 *
 * What the switch changes (trade-off, off by default). With maxPerSlot <= 0 or maxTotal <= 0 the call answers 0 at once: the
 * ChargeableItemsEvent is not posted (its listeners do not run) and no item is offered 0 (Powah's own items write their
 * stored energy component again even for 0). A transmitter or battery that holds energy and charges is unchanged. Runtime
 * flag bons_and_furious.powahIdlePlayerChargeSkip.
 */
public final class PowahPlayerCharge {
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.powahIdlePlayerChargeSkip", "true"));
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    private static volatile boolean announced;

    private PowahPlayerCharge() {
    }

    /** True when the call can only offer 0 and the switch answers 0 for it. */
    public static boolean idle(long maxPerSlot, long maxTotal) {
        if (!enabled || maxPerSlot > 0L && maxTotal > 0L) return false;
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: powah_idle_player_charge_skip applies (an empty Player Transmitter or Battery leaves the player's inventory alone)");
        }
        return true;
    }
}
