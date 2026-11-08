package bons.furious.patch.mobgrindingutils_coo49;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch mobgrindingutils_fan_sync_dedup (since 1.0.49; Mob Grinding Utils 1.1.10+mc1.21.1; server side;
 * NeoForge 21.1). Helper of FanSyncDedupMixin; no Mob Grinding Utils or Minecraft code is carried.
 *
 * What the mod does. Every fan recomputes its blow area on every server tick (TileEntityFan.setAABBWithModifiers) and ends
 * with level.sendBlockUpdated(pos, state, state, 8); for a block entity that makes the chunk holder ask the fan for
 * getUpdatePacket() at the next broadcast, which Mob Grinding Utils answers with a packet carrying the fan's whole update
 * tag, sent to every player tracking the chunk, twenty times a second, whether or not anything changed. On the client the
 * packet is a plain loadAdditional of that tag (and onContentsChanged, which does nothing on a client).
 *
 * What the fan's update tag holds (getUpdateTag = a new tag + saveAdditional): NeoForge's BlockEntity part ("NeoForgeData"
 * when the block entity has persistent data, "neoforge:attachments" when it has serializable data attachments), the
 * inventory ("Items": every non-empty slot as slot index and ItemStack.save, i.e. the item id, the count and the stack's
 * data component patch), "showRenderBox" and the six blow distances (floats).
 *
 * What the switch does (exact variant). When the fan has neither persistent data nor data attachments, its update tag is a
 * function of: showRenderBox, the six floats (by their bits), the slot count, and per slot whether it is empty, the item,
 * the count and the stack's data component patch (by value). The switch keeps those values as they were when the fan last
 * SENT a packet (Snapshot; the patch is ItemStack.getComponentsPatch(), a copy-on-write snapshot of the stack's patch whose
 * component values are immutable). At getUpdatePacket it compares the fan's current values with them: equal, and no
 * different state handed out since -> it returns null (the chunk holder then sends no block entity packet, as for any
 * block entity without one) without building the tag at all; otherwise the packet goes out:
 * ClientboundBlockEntityDataPacket.create(this), which is what the mod's getUpdatePacket returns (the tag that method
 * builds first and drops is a pure computation, so it is not built), and the values are kept anew. Every other hand-out of
 * the fan's state (getUpdateTag outside that call: the chunk data a player receives on starting to track the chunk, any
 * other reader) is compared the same way; a different one makes the next packet go out whatever it holds, so a player who
 * received the fan's state in between is brought back in step exactly as before. A fan that has persistent data or a data
 * attachment is left to the mod's own code (every packet goes out). Hence every client holds, after each broadcast, the
 * same fan state as without the switch; the same-state block update packet still goes out. One difference, labelled:
 * packets identical to the previous one are not sent, so a client keeps its fan's objects (the upgrade stacks are not
 * re-created from an identical tag every tick).
 *
 * Persistent data: NeoForge's BlockEntity.customPersistentData read through a getter handle (never created); unreadable ->
 * the fast path is off. Data attachments: AttachmentHolder.hasAttachments().
 *
 * -Dbons_and_furious.mobgrindingutilsFanSyncDedup=false sends every packet (the original). SHADOW MODE (verification only):
 * -Dbons_and_furious.mobgrindingutilsFanSyncDedup.shadow=true never leaves a packet out; where the switch would have left
 * one out, the packet's tag is compared with the last sent tag (SHADOW_CHECKS, WOULD_SKIP, SHADOW_MISMATCHES: a would-skip
 * whose tag differs, WARN for the first 20).
 */
public final class FanSync {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.mobgrindingutilsFanSyncDedup", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.mobgrindingutilsFanSyncDedup.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), WOULD_SKIP = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    /** Statistics for probes (plain fields): packets left out. */
    public static long skipped;
    private static volatile boolean announced;
    private static final MethodHandle PERSISTENT_DATA;
    /** False when NeoForge's persistent-data field could not be read: then every fan runs the mod's own code. */
    public static final boolean READY;

    static {
        MethodHandle pd = null;
        try {
            Field f = BlockEntity.class.getDeclaredField("customPersistentData");
            f.setAccessible(true);
            pd = MethodHandles.lookup().unreflectGetter(f).asType(MethodType.methodType(Object.class, BlockEntity.class));
        } catch (Throwable t) {
            LOGGER.warn("Bons and Furious: mobgrindingutils_fan_sync_dedup is inactive: NeoForge's block entity data field is not readable ({})", t.toString());
        }
        PERSISTENT_DATA = pd;
        READY = pd != null;
    }

    private FanSync() {
    }

    /** The values a fan's update tag is made of, as they were when it last sent a packet. */
    public static final class Snapshot {
        boolean show;
        int x, y, z, xn, yn, zn;
        int size;
        Item[] items = new Item[0];
        int[] counts = new int[0];
        DataComponentPatch[] patches = new DataComponentPatch[0];
    }

    /** No NeoForge persistent data and no data attachments on the block entity. */
    public static boolean blockEntityPlain(BlockEntity be) {
        if (!READY || be.hasAttachments()) return false;
        try {
            return (Object) PERSISTENT_DATA.invokeExact(be) == null;
        } catch (Throwable t) {
            return false;
        }
    }

    /** The packet's tag (null for a packet without one). */
    public static CompoundTag tagOf(ClientboundBlockEntityDataPacket packet) {
        return packet == null ? null : packet.getTag();
    }

    /** True when the fan's values equal the snapshot. */
    public static boolean unchanged(Snapshot s, boolean show, float x, float y, float z, float xn, float yn, float zn, Container inv) {
        if (s.show != show || s.x != Float.floatToRawIntBits(x) || s.y != Float.floatToRawIntBits(y) || s.z != Float.floatToRawIntBits(z)
                || s.xn != Float.floatToRawIntBits(xn) || s.yn != Float.floatToRawIntBits(yn) || s.zn != Float.floatToRawIntBits(zn)) return false;
        int n = inv.getContainerSize();
        if (n != s.size) return false;
        for (int i = 0; i < n; i++) {
            ItemStack st = inv.getItem(i);
            Item kept = s.items[i];
            if (st.isEmpty()) {
                if (kept != null) return false;
                continue;
            }
            if (kept != st.getItem() || s.counts[i] != st.getCount() || !s.patches[i].equals(st.getComponentsPatch())) return false;
        }
        return true;
    }

    /** Keeps the fan's values. */
    public static void capture(Snapshot s, boolean show, float x, float y, float z, float xn, float yn, float zn, Container inv) {
        int n = inv.getContainerSize();
        if (s.items.length != n) {
            s.items = new Item[n];
            s.counts = new int[n];
            s.patches = new DataComponentPatch[n];
        }
        for (int i = 0; i < n; i++) {
            ItemStack st = inv.getItem(i);
            if (st.isEmpty()) {
                s.items[i] = null;
                s.patches[i] = null;
                continue;
            }
            s.items[i] = st.getItem();
            s.counts[i] = st.getCount();
            s.patches[i] = st.getComponentsPatch();
        }
        s.size = n;
        s.show = show;
        s.x = Float.floatToRawIntBits(x);
        s.y = Float.floatToRawIntBits(y);
        s.z = Float.floatToRawIntBits(z);
        s.xn = Float.floatToRawIntBits(xn);
        s.yn = Float.floatToRawIntBits(yn);
        s.zn = Float.floatToRawIntBits(zn);
    }

    /** Called when a packet is left out: statistics and the one INFO line. */
    public static void leftOut() {
        skipped++;
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: mobgrindingutils_fan_sync_dedup applies (a fan sends its block entity packet only when its state differs from the last one it sent)");
        }
    }

    /** Shadow mode: the would-be-left-out packet's tag against the last sent tag. */
    public static void shadowCheck(CompoundTag lastSent, CompoundTag tag) {
        SHADOW_CHECKS.incrementAndGet();
        WOULD_SKIP.incrementAndGet();
        if (!same(lastSent, tag)) {
            long m = SHADOW_MISMATCHES.incrementAndGet();
            if (m <= 20) LOGGER.warn("Bons and Furious: mobgrindingutils_fan_sync_dedup shadow mismatch #{}: a fan would have left out a packet whose data differs from the last one it sent", m);
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
