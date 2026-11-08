package bons.furious.patch.powah_coo49;

import com.google.common.primitives.Ints;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious 1.0.49 switch powah_container_charge_streamless (Powah 6.2.10, server; kind opt, on by default; helper of
 * PowahContainerChargeMixin, and the walk PowahContainerIdleMixin uses too). No Powah code is carried: this is our own loop
 * over the same calls.
 *
 * What Powah does. ChargeUtil.chargeItemsInContainer (an Energy Hopper's tick, once per tick per hopper) builds
 * IntStream.range(0, container.getContainerSize()).mapToObj(container::getItem).toList() (every getItem call happens while
 * the list is built, in slot order, before any energy moves), then walks that list (transferSlotList): a non-empty stack is
 * asked for its item energy capability (stack.getCapability(Capabilities.EnergyStorage.ITEM), skipped when null) and offered
 * Ints.saturatedCast(min(maxPerSlot, maxTotal - charged)) with receiveEnergy(amount, false); finally container.setChanged()
 * and the total.
 *
 * What the switch does. The same getContainerSize call, the same getItem calls in the same order into a plain array (no
 * range, stream, lambda or list objects), then the same loop with the same capability lookups, offers, setChanged and total
 * (also the same exceptions at the same point: a null stack fails at its isEmpty() in both, after the earlier offers).
 * Runtime flag bons_and_furious.powahContainerChargeStreamless.
 */
public final class PowahContainerCharge {
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.powahContainerChargeStreamless", "true"));
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    private static final ItemStack[] NONE = new ItemStack[0];
    private static volatile boolean announced, announcedIdle;

    private PowahContainerCharge() {
    }

    /**
     * chargeItemsInContainer's walk: the same calls in the same order as Powah's. {@code onlyWhenCharged} (the
     * powah_container_charge_idle_skip change) calls setChanged only when something was charged; false = always, as Powah.
     */
    public static long charge(Container container, long maxPerSlot, long maxTotal, boolean onlyWhenCharged) {
        if (!onlyWhenCharged) {
            if (!announced) announce();
        } else if (!announcedIdle) {
            announceIdle();
        }
        int n = container.getContainerSize();
        ItemStack[] stacks = n > 0 ? new ItemStack[n] : NONE;
        for (int i = 0; i < n; i++) stacks[i] = container.getItem(i);
        long charged = 0L;
        for (ItemStack stack : stacks) {
            if (stack.isEmpty()) continue;
            IEnergyStorage energy = stack.getCapability(Capabilities.EnergyStorage.ITEM);
            if (energy != null) charged += energy.receiveEnergy(Ints.saturatedCast(Math.min(maxPerSlot, maxTotal - charged)), false);
        }
        if (!onlyWhenCharged || charged > 0L) container.setChanged();
        return charged;
    }

    private static void announce() {
        announced = true;
        LOGGER.info("Bons and Furious: powah_container_charge_streamless applies (Energy Hopper charging walks the container's slots without building a stream and a list)");
    }

    private static void announceIdle() {
        announcedIdle = true;
        LOGGER.info("Bons and Furious: powah_container_charge_idle_skip applies (an Energy Hopper with no energy or no charge rate leaves its container alone; the container is marked changed only when something was charged)");
    }
}
