package bons.furious.patch.powah_coo49;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.Tag;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import owmii.powah.lib.logistics.energy.Energy;

/**
 * Bons and Furious 1.0.49 switch powah_item_energy_same_value_write_skip (Powah 5.0.11, both sides wherever a Powah item
 * is charged or drained, in practice the server; kind opt, on by default; helper of PowahItemEnergyWriteMixin). No Powah
 * code is carried.
 *
 * What Powah does. Energy$Item (the energy wrapper of every Powah battery, capacitor and other energy item) rewrites the
 * stack's NBT after every non-simulated receiveEnergy / extractEnergy / consume / setStoredAndWrite (extractEnergy twice:
 * once through consume): write() = stack.getOrCreateTagElement("powah_tile_data"), then Energy.write(that compound, false,
 * false) = putLong("energy_stored_" + "main_energy", stored), the key concatenated anew each time. An item in a charger,
 * discharger or Energy Hopper does this every tick, also when nothing moved (full, empty, rate 0).
 *
 * What the switch does, at that Energy.write(compound, false, false) call of an Energy$Item of exactly that class (a subclass
 * could override the Energy write methods): getOrCreateTagElement has run as before (it still creates a fresh item's tag
 * and compound), then
 *   - the compound already holds energy_stored_main_energy as a LongTag equal to stored: nothing is written (Powah's put
 *     would replace that tag with an equal one: same key, same type, same value, same saved bytes);
 *   - otherwise: the same putLong with the same key (a constant string instead of a new one) and the same value.
 * So every NBT tree, tag type and saved byte is the same as Powah's after every call; only an unchanged LongTag object stays
 * the old one. Runtime flag bons_and_furious.powahItemEnergySameValueWriteSkip.
 */
public final class PowahItemWrites {
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.powahItemEnergySameValueWriteSkip", "true"));
    /** The compound and key Powah writes (ItemStack.getOrCreateTagElement key; "energy_stored_" + "main_energy"). */
    public static final String COMPOUND = "powah_tile_data", KEY = "energy_stored_main_energy";
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    private static volatile boolean announced;

    private PowahItemWrites() {
    }

    /**
     * Energy.write(nbt, capacity, transfer) for {@code self}, called from Energy$Item.write() on the compound
     * getOrCreateTagElement answered: true when the switch did the write (or found it unchanged), false when Powah's own write
     * must run (switch off, another class, other flags).
     */
    public static boolean write(Energy self, CompoundTag nbt, boolean capacity, boolean transfer) {
        if (!enabled || capacity || transfer || nbt == null || self.getClass() != Energy.Item.class) return false;
        long stored = self.getEnergyStored();   // Energy.stored (Energy$Item does not override the getter or the writes)
        Tag held = nbt.m_128423_(KEY);
        if (held == null || held.getClass() != LongTag.class || ((LongTag) held).m_7046_() != stored) nbt.m_128356_(KEY, stored);
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: powah_item_energy_same_value_write_skip applies (a Powah item's energy tag is no longer rewritten with the value it already holds)");
        }
        return true;
    }
}
