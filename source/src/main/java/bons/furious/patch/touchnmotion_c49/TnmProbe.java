package bons.furious.patch.touchnmotion_c49;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.transformer.meta.MixinMerged;

/**
 * Bons and Furious switch touchnmotion_ground_probe_direct (Touch'n Motion 1.0.4 for NeoForge 1.21.1, tested build;
 * client; since 1.0.49). Idea: Collections Of Optimizations (touchnmotion.fastStrideProbe, touchnmotion.fastFloor), idea
 * text only. Touch'n Motion is GPL-3.0; none of its code is carried, the redirects only route its probes.
 *
 * Touch'n Motion probes the ground under a player with `player.level().clip(new ClipContext(from, to, COLLIDER, NONE,
 * player))`: up to 289 floor samples per balance solve (TerrainBalance.floor) and 3 x 9 probes per leg while a stance
 * levels a step (Stride.level), every frame the player moves. Each probe builds a ClipContext and, through its
 * CollisionContext constructor, an EntityCollisionContext (CollisionContext.of(player): the player's sneak flag through
 * SynchedEntityData, its Y, its main-hand item, a fluid predicate), and BlockGetter.clip's step reads every crossed
 * block's fluid state although Fluid.NONE never looks at it.
 *
 * What the switch does. The redirected `new ClipContext(..)` of a probe (COLLIDER, Fluid.NONE, a player, inside the render
 * frame on its thread) keeps its five values and gives INERT; the redirected clip right after it walks the same segment with
 * vanilla's own traversal (BlockGetter.traverseBlocks) and a step that makes the calls of BlockGetter.clip's step for
 * COLLIDER: getBlockState, ClipContext.Block.COLLIDER's shape for the player's collision context, and the level's
 * clipWithInteractionOverride; the miss result is built the same way. Why identical: for Fluid.NONE the fluid shape is
 * empty and its clip null, so clip's step answers exactly the block hit (the same reasoning as vanilla_raycast_fluid_none);
 * the collision context is made once per player, level and frame with CollisionContext.of(player), the factory the
 * ClipContext constructor calls, and nothing it reads (sneak flag, Y, held item) changes while the frame draws (TnmFrame).
 * Every other probe (OUTLINE, a fluid mode, another thread, outside the frame) runs as shipped.
 *
 * Steps aside, once per level class with one WARN line (the probe then runs as shipped): when the level's class or a
 * superclass declares its own clip (clip; another mod's ship or sub-level raycast), except Bons and Furious'
 * vanilla_raycast_fluid_none clip, which answers the same; when a class below Level declares its own getFluidState; when
 * another mod merged code into ClipContext or BlockGetter (Mixin's @MixinMerged; Radium's world.raycast @Overwrite of
 * BlockGetter.clip, for one).
 *
 * -Dbons_and_furious.touchnmotionGroundProbeDirect=false runs every probe as shipped.
 * -Dbons_and_furious.touchnmotionGroundProbeDirect.shadow=true (verification) walks each probe and also makes the shipped
 * call, compares the hit results (type, location, face, block, inside; SHADOW_CHECKS / SHADOW_MISMATCHES, first 20 logged)
 * and uses the shipped answer.
 */
public final class TnmProbe {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.touchnmotionGroundProbeDirect", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.touchnmotionGroundProbeDirect.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    /** Render-thread counters for the harness and the rig: probes walked directly, probes left to the shipped call. */
    public static long walked, shipped;
    static final String OWN_CLIP = "bons.furious.mixin.vanilla_raycast.LevelClipMixin";

    // the probe a redirected `new ClipContext(..)` hands (it gives INERT) to the redirected clip right after it
    private static Vec3 pendingFrom, pendingTo;
    private static Entity pendingEntity;
    private static Level pendingLevel;
    private static boolean pendingMemo;

    // the collision context of the frame's current player (what `new ClipContext(.., player)` makes for every probe)
    private static Entity ccEntity;
    private static Level ccLevel;
    private static int ccFrame;
    private static CollisionContext cc;

    static final class Probe {
        Vec3 from, to;
        Level level;
        CollisionContext context;
        boolean memo, busy;
    }

    private static final Probe PROBE = new Probe();
    /**
     * What a redirected constructor of a walked probe returns (Mixin refuses null from a constructor redirect): never
     * clipped, the redirected clip right after it takes the kept values instead. Made with the empty collision context
     * (1.21.1's Entity constructor calls CollisionContext.of without a null check), so it holds no entity.
     */
    public static final ClipContext INERT = new ClipContext(Vec3.ZERO, Vec3.ZERO, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty());
    private static Class<?> lastClass;
    private static boolean lastOk;
    private static volatile boolean announced;
    private static final boolean CONTEXT_PLAIN = plain(ClipContext.class) && plain(BlockGetter.class);

    /** Per level class: "" when the probe may walk directly, else why not (logged once). */
    private static final ClassValue<String> LEVEL = new ClassValue<>() {
        @Override
        protected String computeValue(Class<?> type) {
            String why = levelProblem(type);
            if (why == null) return "";
            LOGGER.warn("Bons and Furious: touchnmotion_ground_probe_direct steps aside for {}: {}; its probes run as shipped", type.getName(), why);
            return why;
        }
    };

    private TnmProbe() {
    }

    /** Probe sites: Stride.level, TerrainBalance.floor (both this switch's), the wall / support / crouch sites (touchnmotion_pose_block_memo's). */
    public static final int STRIDE = 0, FLOOR = 1, MEMO_SITE = 2;

    /**
     * The redirected `new ClipContext(from, to, block, fluid, entity)` of a probe site. Only floor probes keep their block reads in
     * the frame's memo (TnmMemo): their 81 to 289 samples cross the same few blocks; the other probes cross too few blocks
     * twice for a memo to pay for itself (measured: Stride.level 120 ns per call without it, 144 ns with it).
     * A probe this switch does not walk gets the shipped constructor and clip (plain @Redirect, so the hand-over adds no
     * argument arrays: measured against the jar's own classes on levels where it steps aside).
     */
    public static ClipContext context(Vec3 from, Vec3 to, ClipContext.Block block, ClipContext.Fluid fluid, Entity entity, int site) {
        if (enabled && block == ClipContext.Block.COLLIDER && fluid == ClipContext.Fluid.NONE && entity != null && TnmFrame.here()
                && !PROBE.busy && (site != MEMO_SITE || TnmMemo.enabled)) {
            Level level = entity.level();
            boolean ok = false;
            if (level != null) {
                Class<?> c = level.getClass();
                if (c == lastClass) ok = lastOk;            // the frame's level class, decided once (render thread only)
                else {
                    ok = LEVEL.get(c).isEmpty();
                    lastOk = ok;
                    lastClass = c;
                }
            }
            if (ok) {
                pendingFrom = from;
                pendingTo = to;
                pendingEntity = entity;
                pendingLevel = level;
                pendingMemo = site == FLOOR && TnmFrame.memoArmed && TnmMemo.enabled;
                return INERT;     // the redirected clip right after this call takes the kept values
            }
        }
        return new ClipContext(from, to, block, fluid, entity);
    }

    /** The redirected `level.clip(context)` right after it, when the constructor gave INERT (a real context is clipped by the redirect itself). */
    public static BlockHitResult clip(Level level) {
        Vec3 from = pendingFrom, to = pendingTo;
        Entity entity = pendingEntity;
        Level checked = pendingLevel;
        boolean memo = pendingMemo;
        pendingFrom = pendingTo = null;
        pendingEntity = null;
        pendingLevel = null;
        if (level != checked) {
            // not in 1.0.4: every site clips on the probing player's own level right after building the context
            shipped++;
            return level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, entity));
        }
        if (SHADOW) {
            BlockHitResult ours = walk(level, from, to, entity, memo);
            BlockHitResult theirs = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, entity));
            shadow(ours, theirs);
            return theirs;
        }
        return walk(level, from, to, entity, memo);
    }

    static BlockHitResult walk(Level level, Vec3 from, Vec3 to, Entity entity, boolean memo) {
        if (!announced) announce();
        int f = TnmFrame.frame;
        if (entity != ccEntity || level != ccLevel || f != ccFrame) {
            CollisionContext c = CollisionContext.of(entity);
            cc = c;
            ccEntity = entity;
            ccLevel = level;
            ccFrame = f;
        }
        Probe p = PROBE;
        p.from = from;
        p.to = to;
        p.level = level;
        p.context = cc;
        p.memo = memo;
        if (memo) TnmMemo.begin(level);
        p.busy = true;
        try {
            walked++;
            return BlockGetter.traverseBlocks(from, to, p, TnmProbe::step, TnmProbe::miss);
        } finally {
            p.busy = false;
        }
    }

    /** One block of the walk: BlockGetter.clip's step for COLLIDER without the fluid read (Fluid.NONE ignores it). */
    private static BlockHitResult step(Probe p, BlockPos pos) {
        Level level = p.level;
        BlockState state;
        VoxelShape shape;
        if (p.memo) {
            TnmMemo.Entry e = TnmMemo.entry(level, pos);
            state = e.state;
            shape = TnmMemo.collision(e, level, pos, p.context);
        } else {
            state = level.getBlockState(pos);
            shape = ClipContext.Block.COLLIDER.get(state, level, pos, p.context);
        }
        return level.clipWithInteractionOverride(p.from, p.to, pos, shape, state);
    }

    private static BlockHitResult miss(Probe p) {
        Vec3 back = p.from.subtract(p.to);
        return BlockHitResult.miss(p.to, Direction.getNearest(back.x, back.y, back.z), BlockPos.containing(p.to));
    }

    /** Frame end: let go of the level and the players. */
    static void release() {
        ccEntity = null;
        ccLevel = null;
        cc = null;
        Probe p = PROBE;
        p.from = p.to = null;
        p.level = null;
        p.context = null;
        pendingFrom = pendingTo = null;
        pendingEntity = null;
        pendingLevel = null;
    }

    /** Why a level of this class must keep the shipped probe, or null. */
    static String levelProblem(Class<?> type) {
        if (!CONTEXT_PLAIN) return "another mod changes ClipContext or BlockGetter";
        if (!Level.class.isAssignableFrom(type)) return "not a Level";
        try {
            for (Class<?> c = type; c != null; c = c.getSuperclass()) {
                for (Method m : c.getDeclaredMethods()) {
                    if (m.getParameterCount() != 1) continue;
                    String n = m.getName();
                    if (n.equals("clip") && m.getParameterTypes()[0] == ClipContext.class) {
                        MixinMerged merged = m.getAnnotation(MixinMerged.class);
                        if (merged == null || !OWN_CLIP.equals(merged.mixin()))
                            return c.getName() + " has its own clip" + (merged != null ? " (from " + merged.mixin() + ")" : "");
                    } else if (c != Level.class && n.equals("getFluidState") && m.getParameterTypes()[0] == BlockPos.class) {
                        return c.getName() + " has its own getFluidState";
                    }
                }
                if (c == Level.class) break;
            }
        } catch (Throwable t) {
            return "its class could not be inspected (" + t + ")";
        }
        return null;
    }

    /** No method another mod merged into the class (Bons and Furious' own accessors are fine). */
    static boolean plain(Class<?> c) {
        try {
            for (Method m : c.getDeclaredMethods()) {
                MixinMerged merged = m.getAnnotation(MixinMerged.class);
                if (merged != null && !merged.mixin().startsWith("bons.furious.")) return false;
            }
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private static void shadow(BlockHitResult ours, BlockHitResult theirs) {
        SHADOW_CHECKS.incrementAndGet();
        if (key(ours).equals(key(theirs))) return;
        long m = SHADOW_MISMATCHES.incrementAndGet();
        if (m <= 20) LOGGER.warn("Bons and Furious: touchnmotion_ground_probe_direct shadow mismatch #{}: {} where the shipped probe answered {}", m, key(ours), key(theirs));
    }

    static String key(BlockHitResult h) {
        return h == null ? "null" : h.getType() + " " + h.getLocation() + " " + h.getDirection() + " " + h.getBlockPos() + " " + h.isInside();
    }

    private static synchronized void announce() {
        if (announced) return;
        announced = true;
        LOGGER.info("Bons and Furious: touchnmotion_ground_probe_direct applies (Touch'n Motion's ground probes walk the ray with one collision context per player and frame){}",
                SHADOW ? " - shadow verification on" : "");
    }
}
