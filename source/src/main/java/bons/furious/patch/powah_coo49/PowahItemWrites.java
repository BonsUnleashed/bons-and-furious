package bons.furious.patch.powah_coo49;

import net.minecraft.world.item.ItemStack;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import owmii.powah.components.PowahComponents;
import owmii.powah.lib.logistics.energy.Energy;

/**
 * Bons and Furious 1.0.49 switch powah_item_energy_same_value_write_skip (Powah 6.2.10, both sides wherever a Powah item
 * is charged or drained, in practice the server; kind opt, on by default; helper of PowahItemEnergyWriteMixin). No Powah
 * code is carried.
 *
 * What Powah does. Energy$Item (the energy wrapper of every Powah battery, capacitor and other energy item; a new one for
 * every item capability lookup) writes the charge back after every non-simulated receiveEnergy / extractEnergy / consume /
 * setStoredAndWrite: write() = stack.set(PowahComponents.ENERGY_STORED, Long.valueOf(getStored())), the stack's
 * powah:energy_stored data component. It does so also when nothing moved: an offer of 0 (an empty hopper or transmitter), a
 * charging Battery's consume of 0 every tick it has nothing to charge, and every extractEnergy, which writes once through
 * consume and then again with the same value. Each such set boxes the value and goes through PatchedDataComponentMap.set
 * (a new Optional, and a copy of the patch map while the stack still shares it with a copy of itself).
 *
 * What the switch does, at the head of write() for an Energy$Item of exactly that class (a subclass could override
 * getStored): when the item's default components have no energy component and the stack's own energy component already
 * holds getStored(), the set is left out. Powah's set would then put an equal Optional in place of an equal one (the value
 * differs from the absent default, so it is a put and never a removal): the same patch by value, the same item data, saved
 * bytes, hash and stacking. In every other case (no component yet, another value, an empty stack, a default energy
 * component on the item) Powah's write runs. Runtime flag bons_and_furious.powahItemEnergySameValueWriteSkip.
 */
public final class PowahItemWrites {
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.powahItemEnergySameValueWriteSkip", "true"));
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    private static volatile boolean announced;

    private PowahItemWrites() {
    }

    /** True when Energy$Item.write() for {@code self} could not change {@code stack}: the switch leaves the set out. */
    public static boolean unchanged(Energy.Item self, ItemStack stack) {
        if (!enabled || stack == null || self.getClass() != Energy.Item.class) return false;
        if (stack.getPrototype().has(PowahComponents.ENERGY_STORED)) return false;
        Long held = stack.get(PowahComponents.ENERGY_STORED);
        if (held == null || held.longValue() != self.getStored()) return false;
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: powah_item_energy_same_value_write_skip applies (a Powah item's energy component is no longer rewritten with the value it already holds)");
        }
        return true;
    }
}
