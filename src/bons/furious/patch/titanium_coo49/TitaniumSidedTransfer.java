package bons.furious.patch.titanium_coo49;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.CompoundContainer;
import net.minecraft.world.Container;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.wrapper.InvWrapper;
import net.minecraftforge.items.wrapper.SidedInvWrapper;
import org.apache.logging.log4j.LogManager;

/**
 * Bons and Furious switch titanium_sided_transfer_empty_skip (since 1.0.49; Titanium 3.8.35, server side in effect; helper of
 * SidedTransferEmptyMixin; no Titanium code is carried).
 *
 * What Titanium does. SidedInventoryComponent.transfer (every automatic push or pull of a sided machine inventory) first
 * simulates taking from the source slot, then scans every slot of the destination for one that would take that stack
 * (isValidForAnySlot: getSlots, isItemValid, getStackInSlot and getSlotLimit of every destination slot, a chest = 27 or 54
 * slots, modded storage more), and only then checks whether the simulated stack was empty; for an empty one the scan's
 * answer is not used: the slot cache moves on and the method returns false.
 *
 * What the switch does (exact). When the simulated stack is empty the scan is not run (its answer would be thrown away).
 * The scan only asks the destination handler's query methods, which read only (the same assumption every capability cache in
 * the game makes), with one known exception that keeps the scan: a vanilla loot container. Forge's InvWrapper.getStackInSlot
 * is Container.getItem, and RandomizableContainerBlockEntity.getItem (chests, barrels, hoppers, dispensers, shulker boxes
 * and every modded container built on that class) first rolls the container's loot when its loot table is still set. So a
 * machine that pushes into an unopened loot chest makes the chest roll its loot at its next work tick even with nothing to
 * push; leaving the scan out would have left the loot unrolled until the first real item or the first player, who then
 * rolls it with their own luck (bug sweep 2026-11-12, found by review; before that fix this switch left the scan out for
 * every destination). fillsOnRead(dest) therefore answers true, and the scan runs as shipped, for Forge's two container
 * wrappers around a container (or a double chest half) whose loot table is still set, and whenever it cannot look. Everything
 * else is unchanged: same return value, same slot cache, same inventories, same insert and extract calls. Runtime flag
 * bons_and_furious.titaniumSidedTransferEmptySkip.
 */
public final class TitaniumSidedTransfer {
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.titaniumSidedTransferEmptySkip", "true"));
    /** Statistics for probes and the harness (plain field): scans left out. */
    public static long skipped;
    private static volatile boolean announced;
    /** Getters of SidedInvWrapper.inv, RandomizableContainerBlockEntity.lootTable (f_59605_) and CompoundContainer's two halves; all null when one cannot be had. */
    private static final MethodHandle SIDED_INV, LOOT_TABLE, FIRST_HALF, SECOND_HALF;

    static {
        MethodHandle sided = null, loot = null, first = null, second = null;
        try {
            MethodHandles.Lookup own = MethodHandles.lookup();
            sided = MethodHandles.privateLookupIn(SidedInvWrapper.class, own).findGetter(SidedInvWrapper.class, "inv", WorldlyContainer.class);
            loot = MethodHandles.privateLookupIn(RandomizableContainerBlockEntity.class, own)
                    .findGetter(RandomizableContainerBlockEntity.class, "f_59605_", ResourceLocation.class);
            MethodHandles.Lookup compound = MethodHandles.privateLookupIn(CompoundContainer.class, own);
            first = compound.findGetter(CompoundContainer.class, "f_18910_", Container.class);
            second = compound.findGetter(CompoundContainer.class, "f_18911_", Container.class);
        } catch (Throwable t) {
            sided = loot = first = second = null;
            LogManager.getLogger("Bons and Furious").warn("Bons and Furious: titanium_sided_transfer_empty_skip keeps the destination scan for vanilla containers (their loot state cannot be read: {})", t.toString());
        }
        SIDED_INV = sided;
        LOOT_TABLE = loot;
        FIRST_HALF = first;
        SECOND_HALF = second;
    }

    private TitaniumSidedTransfer() {
    }

    /**
     * True when reading this destination can change it, so the scan must run as shipped: Forge's wrapper (InvWrapper or
     * SidedInvWrapper) around a vanilla container, or a double chest half, whose loot table is still set, and any such wrapper
     * while its container cannot be looked at. Any other handler answers false.
     */
    public static boolean fillsOnRead(IItemHandler dest) {
        try {
            Container inv;
            if (dest instanceof InvWrapper wrapper) {
                inv = wrapper.getInv();
            } else if (dest instanceof SidedInvWrapper sided) {
                if (SIDED_INV == null) return true;
                inv = (WorldlyContainer) SIDED_INV.invokeExact(sided);
            } else {
                return false;
            }
            return LOOT_TABLE == null || lootPending(inv, 0);
        } catch (Throwable t) {
            return true;
        }
    }

    private static boolean lootPending(Container inv, int depth) throws Throwable {
        if (inv instanceof RandomizableContainerBlockEntity container) return (ResourceLocation) LOOT_TABLE.invokeExact(container) != null;
        if (inv instanceof CompoundContainer both) {
            return depth > 3 || lootPending((Container) FIRST_HALF.invokeExact(both), depth + 1) || lootPending((Container) SECOND_HALF.invokeExact(both), depth + 1);
        }
        return false;
    }

    /** One destination scan was left out for an empty simulated stack. */
    public static void skipped() {
        skipped++;
        if (!announced) {
            announced = true;
            LogManager.getLogger("Bons and Furious").info("Bons and Furious: titanium_sided_transfer_empty_skip applies (a sided push or pull from an empty slot no longer scans the destination)");
        }
    }
}
