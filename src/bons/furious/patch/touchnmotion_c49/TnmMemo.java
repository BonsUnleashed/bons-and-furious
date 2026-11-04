package bons.furious.patch.touchnmotion_c49;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch touchnmotion_pose_block_memo (Touch'n Motion 1.0.4, tested build; client; since 1.0.49; works
 * together with touchnmotion_ground_probe_direct and does nothing without it). Idea: Collections Of Optimizations
 * (touchnmotion.blockMemo), idea text only. Touch'n Motion is GPL-3.0; none of its code is carried.
 *
 * Touch'n Motion's floor samples lie 1.5/16 of a block apart, so a balance solve's 81 to 289 probes cross the same 9 to 18
 * blocks hundreds of times, each time reading the block state and its collision shape again; its wall, support and
 * crouch probes (WallSurface.clip, SupportSearch.deck / find / clear / valid, WallSqueeze.crouchOffset) cross the same
 * blocks too, and TerrainBalance.floor reads the hit block's state again (and a fence's, wall's or gate's outline) after
 * each probe.
 *
 * What the switch does. Those sites' probes go through the direct probe of touchnmotion_ground_probe_direct (no clip
 * context, no collision context per probe, no fluid reads), and the floor probes of a frame read each block once: the memo
 * keeps, per block position, the state, the collision shape for the collision context it was made with, and the outline
 * shape floor asks for; floor's own re-read of the hit block gets the state the probe read. (The other probes cross too few
 * blocks twice for a memo to pay: they walk without it.) Why identical: the memo lives for one render frame and one level (TnmFrame), inside
 * which no block of the client level changes (packets and ticks run before the frame, Touch'n Motion sets no block);
 * a collision shape is reused only with the same collision context object, an outline only for the same state; the
 * block shapes are functions of (state, level, position, context), and every entity value a shape reads through the
 * context is fixed for the frame. The memo is cleared at the end of every frame and drops nothing it did not read itself.
 *
 * Reads the memo does not serve call the shipped getBlockState / getShape directly.
 *
 * -Dbons_and_furious.touchnmotionPoseBlockMemo=false turns the memo (and the routing of the six extra sites) off.
 * -Dbons_and_furious.touchnmotionPoseBlockMemo.shadow=true (verification) reads every reused value again, counts the ones
 * that differ (SHADOW_CHECKS / SHADOW_MISMATCHES, first 20 logged: a block changed while a frame drew) and uses the new read.
 */
public final class TnmMemo {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.touchnmotionPoseBlockMemo", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.touchnmotionPoseBlockMemo.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    /** Render-thread counters for the harness and the rig: blocks read, reads saved. */
    public static long reads, reused;

    static final class Entry {
        BlockState state;
        CollisionContext owner;
        VoxelShape collision, outline;
    }

    private static final Long2ObjectOpenHashMap<Entry> MAP = new Long2ObjectOpenHashMap<>();
    private static Level level;
    private static int frame;
    private static volatile boolean announced;

    private TnmMemo() {
    }

    /** A direct probe of this frame on this level starts (a new frame or level starts an empty memo). */
    static void begin(Level l) {
        int f = TnmFrame.frame;
        if (l != level || f != frame) {
            clear();
            level = l;
            frame = f;
            if (!announced) announce();
        }
    }

    /** The block at pos as the walk reads it: from the memo, or read once now. */
    static Entry entry(Level l, BlockPos pos) {
        long key = pos.m_121878_();
        Entry e = MAP.get(key);
        if (e == null) {
            BlockState s = l.m_8055_(pos);
            e = new Entry();
            e.state = s;
            MAP.put(key, e);
            reads++;
            return e;
        }
        reused++;
        if (SHADOW) {
            BlockState live = l.m_8055_(pos);
            if (check(live == e.state, "state", pos, e.state, live)) {
                e.state = live;
                e.owner = null;
                e.outline = null;
            }
        }
        return e;
    }

    /** The block's collision shape for this collision context (COLLIDER's shape, as ClipContext.getBlockShape asks it). */
    static VoxelShape collision(Entry e, Level l, BlockPos pos, CollisionContext cc) {
        if (e.owner != cc) {
            VoxelShape s = ClipContext.Block.COLLIDER.m_7544_(e.state, l, pos, cc);
            e.collision = s;
            e.owner = cc;
            return s;
        }
        reused++;
        if (SHADOW) {
            VoxelShape live = ClipContext.Block.COLLIDER.m_7544_(e.state, l, pos, cc);
            if (check(same(live, e.collision), "collision shape", pos, e.collision, live)) e.collision = live;
        }
        return e.collision;
    }

    /** TerrainBalance.floor's getBlockState of the hit block: the state the probe's walk read for it in this frame. */
    public static BlockState state(Level l, BlockPos pos) {
        if (enabled && l == level && frame == TnmFrame.frame && TnmFrame.here()) {
            Entry e = MAP.get(pos.m_121878_());
            if (e != null) {
                reused++;
                if (SHADOW) {
                    BlockState live = l.m_8055_(pos);
                    if (check(live == e.state, "state", pos, e.state, live)) return live;
                }
                return e.state;
            }
        }
        return l.m_8055_(pos);
    }

    /** TerrainBalance.floor's outline shape of a hit fence, wall or gate: made once per block and frame. */
    public static VoxelShape outline(BlockState state, BlockGetter getter, BlockPos pos) {
        if (enabled && getter == level && frame == TnmFrame.frame && TnmFrame.here()) {
            Entry e = MAP.get(pos.m_121878_());
            if (e != null && e.state == state) {
                VoxelShape o = e.outline;
                if (o == null) {
                    o = state.m_60808_(getter, pos);
                    e.outline = o;
                    return o;
                }
                reused++;
                if (SHADOW) {
                    VoxelShape live = state.m_60808_(getter, pos);
                    if (check(same(live, o), "outline shape", pos, o, live)) e.outline = o = live;
                }
                return o;
            }
        }
        return state.m_60808_(getter, pos);
    }

    static boolean same(VoxelShape a, VoxelShape b) {
        return a == b || (a != null && b != null && !Shapes.m_83157_(a, b, BooleanOp.f_82687_));
    }

    /** Shadow bookkeeping: true when the memo's value differed from a new read. */
    private static boolean check(boolean same, String what, BlockPos pos, Object memo, Object live) {
        SHADOW_CHECKS.incrementAndGet();
        if (same) return false;
        long m = SHADOW_MISMATCHES.incrementAndGet();
        if (m <= 20) LOGGER.warn("Bons and Furious: touchnmotion_pose_block_memo shadow mismatch #{}: {} at {} was {} in the memo, {} now", m, what, pos, memo, live);
        return true;
    }

    /** Frame end, or a new frame or level: drop everything (the memo holds collision contexts, which hold players). */
    static void release() {
        clear();
        level = null;
    }

    private static void clear() {
        if (MAP.isEmpty()) return;
        boolean big = MAP.size() > 1024;
        MAP.clear();
        if (big) MAP.trim(256);
    }

    private static synchronized void announce() {
        if (announced) return;
        announced = true;
        LOGGER.info("Bons and Furious: touchnmotion_pose_block_memo applies (Touch'n Motion's probes read each block once per frame){}",
                SHADOW ? " - shadow verification on" : "");
    }
}
