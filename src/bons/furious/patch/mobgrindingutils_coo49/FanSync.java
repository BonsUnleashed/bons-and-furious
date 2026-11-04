package bons.furious.patch.mobgrindingutils_coo49;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch mobgrindingutils_fan_sync_dedup (since 1.0.49; Mob Grinding Utils 1.20.1-1.1.0; server side; Forge
 * 47.4.28). Helper of FanSyncDedupMixin; no Mob Grinding Utils or Minecraft code is carried. SRG names.
 *
 * What the mod does. Every fan recomputes its blow area on every server tick (TileEntityFan.setAABBWithModifiers) and ends
 * with level.sendBlockUpdated(pos, state, state, 8); for a block entity that makes the chunk holder ask the fan for
 * getUpdatePacket() at the next broadcast, which Mob Grinding Utils answers with a packet carrying the fan's whole update
 * tag, sent to every player tracking the chunk, twenty times a second, whether or not anything changed. On the client the
 * packet is a plain load of that tag (and onContentsChanged, which does nothing on a client).
 *
 * What the fan's update tag holds (getUpdateTag = a new tag + saveAdditional, bytecode of 1.1.0 and Forge 47.4.28):
 * Forge's BlockEntity part ("ForgeData" when the block entity has persistent data, "ForgeCaps" when it has attached
 * capabilities), the inventory ("Items": every non-empty slot as slot index, item id, count, the stack's tag and, when the
 * stack has serializable capabilities, its "ForgeCaps"), "showRenderBox" and the six blow distances (floats).
 *
 * What the switch does (exact variant). When the fan has neither persistent data nor attached capabilities and none of its
 * stacks writes capability data, its update tag is a function of: showRenderBox, the six floats (by their bits), the slot
 * count, and per slot whether it is empty, the item, the count and the stack's tag (by value). The switch keeps those
 * values as they were when the fan last SENT a packet (Snapshot). At getUpdatePacket it compares the fan's current values
 * with them: equal, and no different state handed out since -> it returns null (the chunk holder then sends no block
 * entity packet, as for any block entity without one) without building the tag at all; otherwise the packet goes out:
 * ClientboundBlockEntityDataPacket.create(this), which is what the mod's getUpdatePacket returns (the tag that method
 * builds first and drops is a pure computation for such a fan, so it is not built), and the values are kept anew. Every
 * other hand-out of the fan's state (getUpdateTag outside that call: the chunk data a player receives on starting to track
 * the chunk, any other reader) is compared the same way; a different one makes the next packet go out whatever it holds,
 * so a player who received the fan's state in between is brought back in step exactly as before. A fan that has persistent
 * data, attached capabilities or a stack with capability data is left to the mod's own code (every packet goes out).
 * Hence every client holds, after each broadcast, the same fan state as without the switch; the same-state block update
 * packet still goes out. One difference, labelled: packets identical to the previous one are not sent, so a client keeps
 * its fan's objects (the upgrade stacks are not re-created from an identical tag every tick).
 *
 * Capability probes: Forge's ItemStack.areCapsCompatible((CapabilityDispatcher) null) answers true exactly when the stack has
 * no serializable capability (the same lazy initialisation ItemStack.save performs, so nothing new happens); reached
 * through a method handle (absent outside Forge: then stacks count as capability-free). Persistent data: Forge's
 * BlockEntity.customPersistentData read through a getter handle (never created); unreadable -> the fast path is off.
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
    private static final MethodHandle PERSISTENT_DATA, STACK_CAPS_COMPATIBLE;
    /** False when Forge's persistent-data field could not be read: then every fan runs the mod's own code. */
    public static final boolean READY;

    static {
        MethodHandle pd = null, caps = null;
        try {
            Field f = BlockEntity.class.getDeclaredField("customPersistentData");
            f.setAccessible(true);
            pd = MethodHandles.lookup().unreflectGetter(f).asType(MethodType.methodType(Object.class, BlockEntity.class));
        } catch (Throwable t) {
            LOGGER.warn("Bons and Furious: mobgrindingutils_fan_sync_dedup is inactive: Forge's block entity data field is not readable ({})", t.toString());
        }
        try {
            Class<?> dispatcher = Class.forName("net.minecraftforge.common.capabilities.CapabilityDispatcher", false, FanSync.class.getClassLoader());
            caps = MethodHandles.lookup().findVirtual(ItemStack.class, "areCapsCompatible", MethodType.methodType(boolean.class, dispatcher))
                    .asType(MethodType.methodType(boolean.class, ItemStack.class, Object.class));
        } catch (Throwable t) {
            caps = null;   // not Forge: stacks carry no capabilities
        }
        PERSISTENT_DATA = pd;
        STACK_CAPS_COMPATIBLE = caps;
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
        CompoundTag[] tags = new CompoundTag[0];
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

    /** Every non-empty stack of the inventory writes no capability data. */
    public static boolean stacksPlain(Container inv) {
        int n = inv.m_6643_();
        for (int i = 0; i < n; i++) {
            ItemStack st = inv.m_8020_(i);
            if (!st.m_41619_() && !stackPlain(st)) return false;
        }
        return true;
    }

    /** The packet's tag (null for a packet without one). */
    public static CompoundTag tagOf(net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket packet) {
        return packet == null ? null : packet.m_131708_();
    }

    /** True when the fan's values equal the snapshot (and its stacks are still capability-free). */
    public static boolean unchanged(Snapshot s, boolean show, float x, float y, float z, float xn, float yn, float zn, Container inv) {
        if (s.show != show || s.x != Float.floatToRawIntBits(x) || s.y != Float.floatToRawIntBits(y) || s.z != Float.floatToRawIntBits(z)
                || s.xn != Float.floatToRawIntBits(xn) || s.yn != Float.floatToRawIntBits(yn) || s.zn != Float.floatToRawIntBits(zn)) return false;
        int n = inv.m_6643_();
        if (n != s.size) return false;
        for (int i = 0; i < n; i++) {
            ItemStack st = inv.m_8020_(i);
            Item kept = s.items[i];
            if (st.m_41619_()) {
                if (kept != null) return false;
                continue;
            }
            if (kept != st.m_41720_() || s.counts[i] != st.m_41613_() || !same(s.tags[i], st.m_41783_()) || !stackPlain(st)) return false;
        }
        return true;
    }

    /** Keeps the fan's values; false (keep nothing) when a stack writes capability data. */
    public static boolean capture(Snapshot s, boolean show, float x, float y, float z, float xn, float yn, float zn, Container inv) {
        int n = inv.m_6643_();
        if (s.items.length != n) {
            s.items = new Item[n];
            s.counts = new int[n];
            s.tags = new CompoundTag[n];
        }
        for (int i = 0; i < n; i++) {
            ItemStack st = inv.m_8020_(i);
            if (st.m_41619_()) {
                s.items[i] = null;
                s.tags[i] = null;
                continue;
            }
            if (!stackPlain(st)) return false;
            s.items[i] = st.m_41720_();
            s.counts[i] = st.m_41613_();
            CompoundTag t = st.m_41783_();
            s.tags[i] = t == null ? null : t.m_6426_();
        }
        s.size = n;
        s.show = show;
        s.x = Float.floatToRawIntBits(x);
        s.y = Float.floatToRawIntBits(y);
        s.z = Float.floatToRawIntBits(z);
        s.xn = Float.floatToRawIntBits(xn);
        s.yn = Float.floatToRawIntBits(yn);
        s.zn = Float.floatToRawIntBits(zn);
        return true;
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
