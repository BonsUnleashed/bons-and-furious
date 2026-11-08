package bons.furious.patch.sanguinenetworks_coo49;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.PatchedDataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switches sanguinenetworks_sync_dedup (exact for players; change, off: each packet that goes out costs
 * the server a little more to build) and sanguinenetworks_sync_relevant_only (change, off) for Sanguine Neural Networks
 * 1.21.1-2.0.1, since 1.0.49, server side. Helper of SacrificerSyncDedupMixin and SacrificerSyncRelevantMixin; no Sanguine
 * Neural Networks or Minecraft code is carried.
 *
 * What the mod does. Every branch of VSBlockEntity.tick() ends in sync(): setChanged() and level.sendBlockUpdated(pos,
 * state, state, 3), so at the next broadcast the chunk holder asks the block entity for getUpdatePacket(), which the mod
 * answers with ClientboundBlockEntityDataPacket.create(this): the whole update tag (getUpdateTag = saveWithoutMetadata),
 * sent to every player tracking the chunk, twenty times a second, whether anything changed or not (an idle sacrificer: no
 * model, no altar, a full altar or no energy, repeats the same packet forever). On the client the packet is a plain
 * loadWithComponents of that tag (NeoForge's default onDataPacket).
 *
 * What the sacrificer's update tag holds (bytecode of 2.0.1, NeoForge 21.1): NeoForge's BlockEntity part ("NeoForgeData"
 * with persistent data, "neoforge:attachments" with serializable data attachments), "inventory" (NeoForge's
 * ItemStackHandler: every non-empty slot as Slot plus ItemStack.save, i.e. the item id, the count and the stack's data
 * component patch; "Size"), "energy", "catalystUses", "maxCatalystUses", "catalystMult" (a float), "toProduce", "progress",
 * "maxProgress", "altarPos" when set, and the block entity's own data components ("components", written when it has any).
 *
 * sanguinenetworks_sync_dedup (exact for players, off by default). When the block entity has neither persistent data nor
 * data attachments, its update tag is a function of the values above, the slots and its own component map. The switch
 * keeps those values as they were when the block entity last SENT a packet (Snapshot: the number values, the altar, copies
 * of the slots made right after that packet was built, and its component map). The slots are compared with
 * ItemStack.matches (the same item, count and data components, the test vanilla's menus use to decide that a client's
 * copy of a slot is current), the component map by identity (it is replaced, never changed in place, a patched map never
 * counts as the same). The number values are compared first, the slots and the NeoForge checks only when those all repeat,
 * so a running sacrificer, whose progress and energy change every tick, pays a few comparisons and two stack copies per
 * packet. At getUpdatePacket: values equal, and no different state handed out since -> null (the chunk holder then sends
 * no block entity packet, as for a block entity without one); else the mod's packet goes out and its values are kept.
 * Every other hand-out of the state (getUpdateTag outside that call: the chunk data a player gets on starting to track
 * the chunk) is compared the same way; a different one makes the next packet go out whatever it holds, so every client
 * holds after each broadcast the same sacrificer state as without the switch. Persistent data, data attachments, a
 * subclass: the mod's own method, every packet. The same-state block update packet still goes out. Labelled difference:
 * packets identical to the previous one are not sent, so a client keeps its sacrificer's objects (its stacks are not
 * rebuilt from an identical tag every tick).
 *
 * sanguinenetworks_sync_relevant_only (trade-off, off by default). The same, but the energy buffer is not part of the
 * comparison and the progress only by whether it is zero: a packet goes out when the catalyst uses or multiplier, the blood
 * per cycle, the progress starting or resetting (a finished cycle), the maximum progress, the altar or any slot changes. A
 * running sacrificer then sends a packet when a cycle starts and ends instead of every tick. What players can notice: the
 * copy of the progress and the energy the CLIENT holds is not refreshed every tick while it runs (the sacrificer's screen
 * reads them through its own menu data and stays live; a tooltip mod reading the client copy shows the value of the last
 * packet).
 *
 * Runtime flags bons_and_furious.sanguinenetworksSyncDedup, ...SyncRelevantOnly. SHADOW MODE for the exact key
 * (verification only): -Dbons_and_furious.sanguinenetworksSyncDedup.shadow=true never leaves a packet out; where the switch
 * would have, the packet's tag is compared with the last sent tag (SHADOW_CHECKS, WOULD_SKIP, SHADOW_MISMATCHES, WARN for
 * the first 20).
 */
public final class SacrificerSync {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean dedupEnabled = flag("sanguinenetworksSyncDedup");
    public static volatile boolean relevantEnabled = flag("sanguinenetworksSyncRelevantOnly");
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.sanguinenetworksSyncDedup.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), WOULD_SKIP = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    /** Statistics for probes and the harness (plain fields): packets left out by the exact key and by the change key. */
    public static long skipped, skippedRelevant;
    private static final boolean[] ANNOUNCED = new boolean[2];
    private static final MethodHandle PERSISTENT_DATA;
    /** False when NeoForge's persistent-data field could not be read: then every sacrificer runs the mod's own code. */
    public static final boolean READY;

    static {
        MethodHandle pd = null;
        try {
            Field f = BlockEntity.class.getDeclaredField("customPersistentData");
            f.setAccessible(true);
            pd = MethodHandles.lookup().unreflectGetter(f).asType(MethodType.methodType(Object.class, BlockEntity.class));
        } catch (Throwable t) {
            LOGGER.warn("Bons and Furious: the Virtual Sacrificer packet switches are inactive: NeoForge's block entity data field is not readable ({})", t.toString());
        }
        PERSISTENT_DATA = pd;
        READY = pd != null;
    }

    private static boolean flag(String camelName) {
        return !"false".equalsIgnoreCase(System.getProperty("bons_and_furious." + camelName, "true"));
    }

    private SacrificerSync() {
    }

    /** The values of the last packet a sacrificer sent. */
    public static final class Snapshot {
        int energy, catalystUses, maxCatalystUses, catalystMult, toProduce, progress, maxProgress;
        boolean altar;
        int ax, ay, az;
        /** Copies of the slots as that packet wrote them (null: none kept, send). */
        ItemStack[] slots;
        /** The block entity's own component map as that packet wrote it. */
        DataComponentMap components;
    }

    /** No NeoForge persistent data (the field is read, never created) and no data attachments on the block entity. */
    public static boolean blockEntityPlain(BlockEntity be) {
        if (!READY || be.hasAttachments()) return false;
        try {
            return (Object) PERSISTENT_DATA.invokeExact(be) == null;
        } catch (Throwable t) {
            return false;
        }
    }

    /** The packet's tag (null for a packet without one or of another kind). */
    public static CompoundTag tagOf(Packet<?> packet) {
        return packet instanceof ClientboundBlockEntityDataPacket p ? p.getTag() : null;
    }

    /**
     * The sacrificer's number values and altar equal the snapshot's. {@code relevantOnly}: the change key's comparison
     * (energy left out, progress only by whether it is zero). False at the first difference.
     */
    public static boolean valuesSame(Snapshot s, boolean relevantOnly, int energy, int catalystUses, int maxCatalystUses, float catalystMult,
                                     int toProduce, int progress, int maxProgress, BlockPos altar) {
        if (relevantOnly) {
            if ((s.progress == 0) != (progress == 0)) return false;
        } else if (s.energy != energy || s.progress != progress) {
            return false;
        }
        if (s.catalystUses != catalystUses || s.maxCatalystUses != maxCatalystUses || s.catalystMult != Float.floatToRawIntBits(catalystMult)
                || s.toProduce != toProduce || s.maxProgress != maxProgress) return false;
        if (altar == null) return !s.altar;
        return s.altar && s.ax == altar.getX() && s.ay == altar.getY() && s.az == altar.getZ();
    }

    /**
     * Asked when valuesSame holds: the block entity has no NeoForge data or attachments, its own component map is the one
     * the last sent packet wrote, and its slots match that packet's copies (the same size; ItemStack.matches per slot, two
     * empty slots included).
     */
    public static boolean slotsSame(Snapshot s, BlockEntity be, ItemStackHandler inv) {
        ItemStack[] sent = s.slots;
        if (sent == null || !blockEntityPlain(be)) return false;
        DataComponentMap components = be.components();
        if (components != s.components || components instanceof PatchedDataComponentMap) return false;
        int n = inv.getSlots();
        if (n != sent.length) return false;
        for (int i = 0; i < n; i++) {
            if (!ItemStack.matches(inv.getStackInSlot(i), sent[i])) return false;
        }
        return true;
    }

    /** Keeps the values of the packet just built: the numbers, the altar, copies of the slots and the component map. */
    public static void capture(Snapshot s, int energy, int catalystUses, int maxCatalystUses, float catalystMult, int toProduce, int progress,
                               int maxProgress, BlockPos altar, BlockEntity be, ItemStackHandler inv) {
        int n = inv.getSlots();
        ItemStack[] slots = s.slots != null && s.slots.length == n ? s.slots : new ItemStack[n];
        for (int i = 0; i < n; i++) slots[i] = inv.getStackInSlot(i).copy();
        s.slots = slots;
        s.components = be.components();
        s.energy = energy;
        s.catalystUses = catalystUses;
        s.maxCatalystUses = maxCatalystUses;
        s.catalystMult = Float.floatToRawIntBits(catalystMult);
        s.toProduce = toProduce;
        s.progress = progress;
        s.maxProgress = maxProgress;
        s.altar = altar != null;
        if (altar != null) {
            s.ax = altar.getX();
            s.ay = altar.getY();
            s.az = altar.getZ();
        }
    }

    /** A packet was left out ({@code which}: 0 dedup, 1 relevant): statistics and one INFO line. */
    public static void leftOut(int which) {
        if (which == 0) skipped++;
        else skippedRelevant++;
        if (!ANNOUNCED[which]) {
            ANNOUNCED[which] = true;
            LOGGER.info("Bons and Furious: {} applies (a Virtual Sacrificer sends its block entity packet only when {})",
                    which == 0 ? "sanguinenetworks_sync_dedup" : "sanguinenetworks_sync_relevant_only",
                    which == 0 ? "its state differs from the last one it sent" : "a value other than the energy or the running progress changed");
        }
    }

    /** Shadow mode: the would-be-left-out packet's tag against the last sent tag. */
    public static void shadowCheck(String key, CompoundTag lastSent, CompoundTag tag) {
        SHADOW_CHECKS.incrementAndGet();
        WOULD_SKIP.incrementAndGet();
        if (!same(lastSent, tag)) {
            long m = SHADOW_MISMATCHES.incrementAndGet();
            if (m <= 20) LOGGER.warn("Bons and Furious: {} shadow mismatch #{}: a Virtual Sacrificer would have left out a packet whose data differs from the last one it sent", key, m);
        }
    }

    /** Structural equality of two tags, floating point values by their bits. */
    public static boolean same(Tag a, Tag b) {
        if (a == b) return true;
        if (a == null || b == null || a.getId() != b.getId() || a.getClass() != b.getClass()) return false;
        if (a instanceof CompoundTag ca) {
            CompoundTag cb = (CompoundTag) b;
            Set<String> keys = ca.getAllKeys();
            if (keys.size() != cb.getAllKeys().size()) return false;
            for (String k : keys) {
                Tag vb = cb.get(k);
                if (vb == null || !same(ca.get(k), vb)) return false;
            }
            return true;
        }
        if (a instanceof ListTag la) {
            ListTag lb = (ListTag) b;
            if (la.size() != lb.size() || la.getElementType() != lb.getElementType()) return false;
            for (int i = 0; i < la.size(); i++) if (!same(la.get(i), lb.get(i))) return false;
            return true;
        }
        if (a instanceof FloatTag fa) return Float.floatToRawIntBits(fa.getAsFloat()) == Float.floatToRawIntBits(((FloatTag) b).getAsFloat());
        if (a instanceof DoubleTag da) return Double.doubleToRawLongBits(da.getAsDouble()) == Double.doubleToRawLongBits(((DoubleTag) b).getAsDouble());
        return a.equals(b);
    }
}
