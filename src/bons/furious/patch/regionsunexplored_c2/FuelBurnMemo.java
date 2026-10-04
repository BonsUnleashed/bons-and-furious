package bons.furious.patch.regionsunexplored_c2;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.furnace.FurnaceFuelBurnTimeEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch regionsunexplored_fuel_burn_memo (Regions Unexplored 0.5.6+1.20.1, Forge; both sides). No
 * Regions Unexplored code is carried here (its licence is All Rights Reserved): the mixins only wrap its listener and
 * record what it does.
 *
 * Regions Unexplored's FurnaceBurnTimes.burnTime listens to Forge's FurnaceFuelBurnTimeEvent, which Forge posts for every
 * non-empty stack whose burn time anyone asks for: JEI's fuel recipe list (its whole ingredient list on every JEI start,
 * that is every world join), furnace fuel-slot checks (hopper insertion, shift-click), furnace re-lighting and other mods'
 * fuel checks. The listener reads only {@code event.getItemStack().getItem()} and compares that item with 422 of its own
 * items (each comparison a RegistryObject.get() plus Forge's Block.asItem(), a registry delegate lookup) in four if-chains;
 * a match calls {@code event.setBurnTime(300/200/150/100)}. An item that is not one of them (the usual case) costs all 422.
 *
 * The first time an item is seen, the listener runs unchanged while the mixin records every setBurnTime call it makes, in
 * order (none for most items, two for the painted slabs: 300 and then 150). That call list is kept on the Item itself (a
 * field this switch adds to Item, so items are told apart by identity, exactly as the listener's == comparisons do) and
 * later events for that item make the same setBurnTime calls on the event, in the same order, without the comparisons.
 *
 * Why this is identical: which branches run depends only on the item (no NBT, count, recipe type, tag, config or event
 * state is read) and on the RegistryObject values and asItem() results, which are fixed once registration has finished;
 * the event sees the same calls with the same arguments in the same order, so its burn time and cancellation end up the
 * same (Forge's own setBurnTime logic runs on every replayed call). A run that throws (for example a RegistryObject that is
 * not bound yet) records nothing, so the original runs, and throws, again. Two threads (client and integrated server) that
 * meet a new item at the same time both run the original and store equal lists.
 *
 * Runtime flag: -Dbons_and_furious.regionsUnexploredFuelBurnMemo=false runs the original listener on every event.
 * Shadow mode for rigs: -Dbons_and_furious.regionsUnexploredFuelBurnMemo.shadow=true runs the original on every event and
 * compares its recorded calls with the kept list (SHADOW_CHECKS / SHADOW_MISMATCHES, at most 20 WARN lines).
 */
public final class FuelBurnMemo {
    /** Runtime switch (the config switch acts when classes are transformed). */
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.regionsUnexploredFuelBurnMemo", "true"));
    /** Shadow mode (see the class comment): the original answers every event and the kept list is only compared. */
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.regionsUnexploredFuelBurnMemo.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong();
    public static final AtomicLong SHADOW_MISMATCHES = new AtomicLong();
    /** Items whose list was recorded (first run of the listener for that item; for probes and the harness). */
    public static final AtomicLong RECORDED = new AtomicLong();
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    private static final int[] NONE = new int[0];
    /** The distinct call lists seen so far (a handful), so items share one array per list. */
    private static volatile int[][] lists = {NONE};
    private static volatile boolean announced;
    /** The recording of the listener run on this thread, if one is open (a stack, in case a run nests). */
    private static final ThreadLocal<Recording> RECORDING = new ThreadLocal<>();

    /** The field this switch adds to Item (ItemFuelMemoSlotMixin): the listener's call list for that item, or null. */
    public interface Slot {
        int[] bons$ruFuelCalls();

        void bons$ruFuelCalls(int[] calls);
    }

    private static final class Recording {
        final Recording outer;
        int[] calls = NONE;

        Recording(Recording outer) {
            this.outer = outer;
        }

        void add(int time) {
            int[] c = Arrays.copyOf(calls, calls.length + 1);
            c[c.length - 1] = time;
            calls = c;
        }
    }

    private FuelBurnMemo() {
    }

    /** The @WrapMethod handler body for FurnaceBurnTimes.burnTime(event). */
    public static void burnTime(FurnaceFuelBurnTimeEvent event, Operation<Void> original) {
        ItemStack stack;
        if (!enabled || event == null || (stack = event.getItemStack()) == null) {
            original.call(event);       // the original throws its own NullPointerException for a null event or stack
            return;
        }
        Item item = stack.m_41720_();
        Slot slot = (Slot) item;
        int[] kept = slot.bons$ruFuelCalls();
        if (kept != null && !SHADOW) {
            for (int time : kept) event.setBurnTime(time);
            return;
        }
        int[] calls = record(event, original);   // the original, with its setBurnTime calls recorded; throws as it does
        if (kept != null) {
            SHADOW_CHECKS.incrementAndGet();
            if (!Arrays.equals(kept, calls) && SHADOW_MISMATCHES.incrementAndGet() <= 20) {
                LOGGER.warn("Bons and Furious: regionsunexplored_fuel_burn_memo SHADOW MISMATCH for {}: kept {} but the listener called setBurnTime {}",
                        item, Arrays.toString(kept), Arrays.toString(calls));
            }
            return;
        }
        RECORDED.incrementAndGet();
        slot.bons$ruFuelCalls(canonical(calls));
        if (!announced) {
            announced = true;
            LOGGER.info(SHADOW ? "Bons and Furious: regionsunexplored_fuel_burn_memo SHADOW MODE: Regions Unexplored's fuel listener runs on every event; "
                    + "the kept answers are only compared (mismatches as WARN)"
                    : "Bons and Furious: regionsunexplored_fuel_burn_memo: Regions Unexplored's fuel listener answers each item from its first run");
        }
    }

    /** The @WrapOperation handler for the four event.setBurnTime(int) calls inside the listener. */
    public static void setBurnTime(FurnaceFuelBurnTimeEvent event, int time, Operation<Void> original) {
        Recording r = RECORDING.get();
        if (r != null) r.add(time);
        original.call(event, time);
    }

    private static int[] record(FurnaceFuelBurnTimeEvent event, Operation<Void> original) {
        Recording outer = RECORDING.get();
        Recording r = new Recording(outer);
        RECORDING.set(r);
        try {
            original.call(event);
        } finally {
            if (outer == null) RECORDING.remove();
            else RECORDING.set(outer);
        }
        return r.calls;
    }

    private static int[] canonical(int[] calls) {
        int[][] known = lists;
        for (int[] k : known) if (Arrays.equals(k, calls)) return k;
        synchronized (FuelBurnMemo.class) {
            known = lists;
            for (int[] k : known) if (Arrays.equals(k, calls)) return k;
            int[][] grown = Arrays.copyOf(known, known.length + 1);
            grown[known.length] = calls;
            lists = grown;
            return calls;
        }
    }
}
