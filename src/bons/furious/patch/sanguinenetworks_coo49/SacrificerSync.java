package bons.furious.patch.sanguinenetworks_coo49;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.items.ItemStackHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switches sanguinenetworks_sync_dedup (exact for players; change, off: each packet that goes out costs
 * the server about 4 % more to build) and sanguinenetworks_sync_relevant_only (change, off) for Sanguine Neural Networks
 * 2.0.1, and their 1.2.1 twins sanguinenetworks_leo_sync_dedup /
 * sanguinenetworks_leo_sync_relevant_only, since 1.0.49, server side. Helper of the four Sacrificer*Sync mixins; no Sanguine
 * Neural Networks or Minecraft code is carried. SRG names.
 *
 * What the mod does. Every branch of VSBlockEntity.tick() ends in sync(): setChanged() and level.sendBlockUpdated(pos,
 * state, state, 3), so at the next broadcast the chunk holder asks the block entity for getUpdatePacket(), which the mod
 * answers with ClientboundBlockEntityDataPacket.create(this): the whole update tag (getUpdateTag = a new tag +
 * saveAdditional), sent to every player tracking the chunk, twenty times a second, whether anything changed or not (an idle
 * sacrificer: no model, no altar, a full altar or no energy, repeats the same packet forever). On the client the packet is a
 * plain load of that tag.
 *
 * What the sacrificer's update tag holds (bytecode of 2.0.1 and 1.2.1, Forge 47.4.28): Forge's BlockEntity part
 * ("ForgeData" with persistent data, "ForgeCaps" with attached capabilities), "inventory" (Forge's ItemStackHandler: every
 * non-empty slot as Slot, the item id, the count byte, a copy of the stack's tag and, when the stack has serializable
 * capabilities, its "ForgeCaps"; "Size"), "energy", "catalystUses", "maxCatalystUses", "catalystMult" (a float), "toProduce",
 * "progress", "maxProgress" and, when set, "altarPos" (X, Y, Z).
 *
 * sanguinenetworks_sync_dedup (exact for players, off by default). When the block entity has neither persistent data nor attached capabilities
 * and none of its stacks writes capability data, its update tag is a function of the values above (the float by its bits,
 * the stack tags by value). The switch keeps those values as they were when the block entity last SENT a packet (Snapshot:
 * the number values, the altar and that packet's tag, whose inventory part the slots are compared with: the same size and
 * the same non-empty slots with the same item id, count byte and stack tag; nothing writes that tag after it is built:
 * Netty encodes it and a client's load copies every stack tag it reads). The number values are compared first, the slots
 * and the Forge checks only when those all repeat, so a running sacrificer, whose progress and energy change every tick,
 * pays a few comparisons and stores per packet. At getUpdatePacket: values equal, and no different state handed out since
 * -> null (the chunk holder then sends no block entity packet, as for a block entity without one); else the mod's packet
 * goes out and its values are kept. Every other hand-out of the state (getUpdateTag outside that call: the
 * chunk data a player gets on starting to track the chunk) is compared the same way; a different one makes the next packet
 * go out whatever it holds, so every client holds after each broadcast the same sacrificer state as without the switch.
 * Persistent data, attached capabilities, a stack with capability data, a subclass: the mod's own method, every packet. The
 * same-state block update packet still goes out. Labelled difference: packets identical to the previous one are not sent,
 * so a client keeps its sacrificer's objects (its stacks are not rebuilt from an identical tag every tick).
 *
 * sanguinenetworks_sync_relevant_only (trade-off, off by default). The same, but the energy buffer is not part of the
 * comparison and the progress only by whether it is zero: a packet goes out when the catalyst uses or multiplier, the blood
 * per cycle, the progress starting or resetting (a finished cycle), the maximum progress, the altar or any slot changes. A
 * running sacrificer then sends a packet when a cycle starts and ends instead of every tick. What players can notice: the
 * copy of the progress and the energy the CLIENT holds is not refreshed every tick while it runs (the sacrificer's screen
 * reads them through its own menu data and stays live; a tooltip mod reading the client copy shows the value of the last
 * packet).
 *
 * Runtime flags bons_and_furious.sanguinenetworksSyncDedup, ...SyncRelevantOnly, ...LeoSyncDedup, ...LeoSyncRelevantOnly.
 * SHADOW MODE for the exact keys (verification only): -Dbons_and_furious.sanguinenetworksSyncDedup.shadow=true (and
 * ...LeoSyncDedup.shadow) never leaves a packet out; where the switch would have, the packet's tag is compared with the last
 * sent tag (SHADOW_CHECKS, WOULD_SKIP, SHADOW_MISMATCHES, WARN for the first 20).
 */
public final class SacrificerSync {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean dedupEnabled = flag("sanguinenetworksSyncDedup");
    public static volatile boolean relevantEnabled = flag("sanguinenetworksSyncRelevantOnly");
    public static volatile boolean leoDedupEnabled = flag("sanguinenetworksLeoSyncDedup");
    public static volatile boolean leoRelevantEnabled = flag("sanguinenetworksLeoSyncRelevantOnly");
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.sanguinenetworksSyncDedup.shadow");
    public static final boolean LEO_SHADOW = Boolean.getBoolean("bons_and_furious.sanguinenetworksLeoSyncDedup.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), WOULD_SKIP = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    /** Statistics for probes and the harness (plain fields): packets left out by the exact keys and by the change keys. */
    public static long skipped, skippedRelevant;
    private static final boolean[] ANNOUNCED = new boolean[4];
    private static final MethodHandle PERSISTENT_DATA, STACK_CAPS_COMPATIBLE;
    /** False when Forge's persistent-data field could not be read: then every sacrificer runs the mod's own code. */
    public static final boolean READY;

    static {
        MethodHandle pd = null, caps = null;
        try {
            Field f = BlockEntity.class.getDeclaredField("customPersistentData");
            f.setAccessible(true);
            pd = MethodHandles.lookup().unreflectGetter(f).asType(MethodType.methodType(Object.class, BlockEntity.class));
        } catch (Throwable t) {
            LOGGER.warn("Bons and Furious: the Virtual Sacrificer packet switches are inactive: Forge's block entity data field is not readable ({})", t.toString());
        }
        try {
            Class<?> dispatcher = Class.forName("net.minecraftforge.common.capabilities.CapabilityDispatcher", false, SacrificerSync.class.getClassLoader());
            caps = MethodHandles.lookup().findVirtual(ItemStack.class, "areCapsCompatible", MethodType.methodType(boolean.class, dispatcher))
                    .asType(MethodType.methodType(boolean.class, ItemStack.class, Object.class));
        } catch (Throwable t) {
            caps = null;   // not Forge: stacks carry no capabilities
        }
        PERSISTENT_DATA = pd;
        STACK_CAPS_COMPATIBLE = caps;
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
        /** The last sent packet's tag: the slots are compared with its inventory, whose stack tags are copies (null: send). */
        CompoundTag sent;
    }

    /** No Forge persistent data and no attached capabilities on the block entity ({@code caps}: its dispatcher). */
    public static boolean blockEntityPlain(BlockEntity be, Object caps) {
        if (!READY || caps != null) return false;
        try {
            return (Object) PERSISTENT_DATA.invokeExact(be) == null;
        } catch (Throwable t) {
            return false;
        }
    }

    /** The stack writes no capability data (Forge: compatible with no capabilities). */
    static boolean stackPlain(ItemStack s) {
        if (STACK_CAPS_COMPATIBLE == null) return true;
        try {
            return (boolean) STACK_CAPS_COMPATIBLE.invokeExact(s, (Object) null);
        } catch (Throwable t) {
            return false;
        }
    }

    /** The packet's tag (null for a packet without one or of another kind). */
    public static CompoundTag tagOf(Packet<?> packet) {
        return packet instanceof ClientboundBlockEntityDataPacket p ? p.m_131708_() : null;
    }

    /** The entry the sent inventory's list holds for that slot (null when the slot was sent empty). */
    static CompoundTag sentEntry(ListTag items, int slot) {
        for (int i = 0, n = items.size(); i < n; i++) {
            CompoundTag e = items.m_128728_(i);
            if (e.m_128451_("Slot") == slot) return e;
        }
        return null;
    }

    /**
     * The sacrificer's number values and altar equal the snapshot's. {@code relevantOnly}: the change keys' comparison
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
        return s.altar && s.ax == altar.m_123341_() && s.ay == altar.m_123342_() && s.az == altar.m_123343_();
    }

    /**
     * Asked when valuesSame holds: the block entity has no Forge data or capabilities ({@code caps}: its dispatcher) and its
     * slots are what the last sent packet's inventory holds (ItemStackHandler.serializeNBT: the size, and per non-empty slot
     * the item id, the count byte and the stack tag ItemStack.save writes; none of the stacks with capability data).
     */
    public static boolean slotsSame(Snapshot s, BlockEntity be, Object caps, ItemStackHandler inv) {
        CompoundTag sent = s.sent;
        if (sent == null || !sent.m_128425_("inventory", 10) || !blockEntityPlain(be, caps)) return false;
        CompoundTag inventory = sent.m_128469_("inventory");
        int n = inv.getSlots();
        if (inventory.m_128451_("Size") != n) return false;
        ListTag items = inventory.m_128437_("Items", 10);
        int full = 0;
        for (int i = 0; i < n; i++) {
            ItemStack st = inv.getStackInSlot(i);
            if (st.m_41619_()) continue;
            full++;
            CompoundTag e = sentEntry(items, i);
            if (e == null || e.m_128441_("ForgeCaps") || e.m_128445_("Count") != (byte) st.m_41613_() || !sameId(e.m_128461_("id"), st.m_41720_())
                    || !stackPlain(st) || !same(e.m_128425_("tag", 10) ? e.m_128469_("tag") : null, st.m_41783_())) return false;
        }
        return full == items.size();
    }

    /** The sent id is the one ItemStack.save writes for that item (the registry key, "minecraft:air" without one). */
    static boolean sameId(String id, Item item) {
        ResourceLocation key = BuiltInRegistries.f_257033_.m_7981_(item);
        if (key == null) return "minecraft:air".equals(id);
        String ns = key.m_135827_(), path = key.m_135815_();
        return id.length() == ns.length() + 1 + path.length() && id.startsWith(ns) && id.charAt(ns.length()) == ':' && id.endsWith(path);
    }

    /** Keeps the values of the packet just built and its tag ({@code tag}), which the slots are compared with. */
    public static void capture(Snapshot s, int energy, int catalystUses, int maxCatalystUses, float catalystMult, int toProduce, int progress,
                               int maxProgress, BlockPos altar, CompoundTag tag) {
        s.sent = tag;
        s.energy = energy;
        s.catalystUses = catalystUses;
        s.maxCatalystUses = maxCatalystUses;
        s.catalystMult = Float.floatToRawIntBits(catalystMult);
        s.toProduce = toProduce;
        s.progress = progress;
        s.maxProgress = maxProgress;
        s.altar = altar != null;
        if (altar != null) {
            s.ax = altar.m_123341_();
            s.ay = altar.m_123342_();
            s.az = altar.m_123343_();
        }
    }

    /** A packet was left out ({@code which}: 0 dedup, 1 relevant, 2 leo dedup, 3 leo relevant): statistics and one INFO line. */
    public static void leftOut(int which) {
        if ((which & 1) == 0) skipped++;
        else skippedRelevant++;
        if (!ANNOUNCED[which]) {
            ANNOUNCED[which] = true;
            String key = switch (which) {
                case 0 -> "sanguinenetworks_sync_dedup";
                case 1 -> "sanguinenetworks_sync_relevant_only";
                case 2 -> "sanguinenetworks_leo_sync_dedup";
                default -> "sanguinenetworks_leo_sync_relevant_only";
            };
            LOGGER.info("Bons and Furious: {} applies (a Virtual Sacrificer sends its block entity packet only when {})", key,
                    (which & 1) == 0 ? "its state differs from the last one it sent" : "a value other than the energy or the running progress changed");
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
        if (a == null || b == null || a.m_7060_() != b.m_7060_() || a.getClass() != b.getClass()) return false;
        if (a instanceof CompoundTag ca) {
            CompoundTag cb = (CompoundTag) b;
            Set<String> keys = ca.m_128431_();
            if (keys.size() != cb.m_128431_().size()) return false;
            for (String k : keys) {
                Tag vb = cb.m_128423_(k);
                if (vb == null || !same(ca.m_128423_(k), vb)) return false;
            }
            return true;
        }
        if (a instanceof ListTag la) {
            ListTag lb = (ListTag) b;
            if (la.size() != lb.size() || la.m_7264_() != lb.m_7264_()) return false;
            for (int i = 0; i < la.size(); i++) if (!same(la.get(i), lb.get(i))) return false;
            return true;
        }
        if (a instanceof FloatTag fa) return Float.floatToRawIntBits(fa.m_7057_()) == Float.floatToRawIntBits(((FloatTag) b).m_7057_());
        if (a instanceof DoubleTag da) return Double.doubleToRawLongBits(da.m_7061_()) == Double.doubleToRawLongBits(((DoubleTag) b).m_7061_());
        return a.equals(b);
    }
}
