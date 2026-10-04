package bons.furious.patch.vanilla_search;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.MoveToBlockGoal;
import net.minecraft.world.entity.ai.goal.RemoveBlockGoal;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraftforge.common.extensions.IForgeBlock;
import net.minecraftforge.common.extensions.IForgeBlockState;

/**
 * Bons and Furious switch vanilla_turtle_egg_search_sections (Minecraft 1.20.1 on Forge 47.4.16; server side). SRG names.
 *
 * Zombies, husks, drowned, zombie villagers and zombified piglins (and every mod goal built on RemoveBlockGoal, e.g. the
 * turtle-egg and alligator-egg goals of Alex's Mobs and Naturalist) search for their target block every 5-10 seconds:
 * MoveToBlockGoal.findNearestBlock visits 7 layers of 47 x 47 positions around the mob and asks
 * RemoveBlockGoal.isValidTarget at each: getChunk(x, z, FULL, false), then the block state twice
 * (canEntityDestroy, then is(target) and two air checks above). That is about 15,500 positions per attempt.
 *
 * The switch visits the same positions in the same order and makes the same getChunk calls (only a call that repeats the
 * previous call with nothing in between is dropped: that call can only hit getChunk's cache), but it does not read the
 * block of a position whose chunk section cannot hold an interesting state: one that is the target block, or whose block
 * class overrides Forge's canEntityDestroy. For every other state isValidTarget is a pure false: Forge's default
 * canEntityDestroy returns true without side effects for an entity that is not an ender dragon or wither, and is(target)
 * is false. Positions in sections that may hold an interesting state run the real isValidTarget.
 *
 * Applies only where the result is provably the same (decided once per class and cached by class):
 *  - the goal is a RemoveBlockGoal whose class, up to RemoveBlockGoal, declares neither isValidTarget (m_6465_) nor
 *    findNearestBlock (m_25626_); every other MoveToBlockGoal (crows, butterflies, cats, ...) runs unchanged;
 *  - the mob's class keeps Mob.isWithinRestriction (m_21444_) and Mob.hasRestriction (m_21536_), Entity.level (m_9236_)
 *    and Entity.blockPosition (m_20183_), and the mob is not a wither (whose canEntityDestroy default consults Forge's
 *    overridable isAir; ender dragons and wither skulls are no PathfinderMobs);
 *  - BlockState keeps IForgeBlockState's canEntityDestroy; the level is not a debug world.
 * If another mod's code, run by an original test, moves the mob to another level, the rest of that search runs the
 * original per-position code. Assumption (true for vanilla, Radium's chunk_access and ModernFix): a getChunk call that
 * repeats the previous one with nothing in between has no effect beyond returning the same chunk.
 *
 * -Dbons_and_furious.turtleEggSearchSections=false runs the original search.
 * -Dbons_and_furious.turtleEggSearchSections.shadow=true (verification) runs both and compares result and position
 * (SHADOW_CHECKS / SHADOW_MISMATCHES, first 20 logged); the original's answer is used.
 */
public final class TurtleEggSearch {
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.turtleEggSearchSections", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.turtleEggSearchSections.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    /** Searches answered by the switch (also counted in shadow mode). */
    public static final AtomicLong FAST = new AtomicLong();
    /** search(): run the original instead (nothing was called yet). */
    public static final BlockPos FALLBACK = new BlockPos(0, Integer.MIN_VALUE, 0);
    private static final long NO_KEY = Long.MIN_VALUE;
    private static volatile boolean announced;
    private static final boolean STATE_DEFAULT = stateKeepsForgeDefault();

    /** The goal side (MoveToBlockGoalSearchMixin): vanilla's own isValidTarget. */
    public interface Goal {
        boolean bons$isValidTarget(LevelReader level, BlockPos pos);
    }

    private static final ClassValue<Boolean> GOAL_OK = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            if (!RemoveBlockGoal.class.isAssignableFrom(type)) return false;
            for (Class<?> c = type; c != RemoveBlockGoal.class; c = c.getSuperclass()) {
                if (declares(c, "m_6465_", LevelReader.class, BlockPos.class) || declares(c, "m_25626_")) return false;
            }
            return true;
        }
    };

    private static final ClassValue<Boolean> MOB_OK = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            for (Class<?> c = type; c != null && c != Mob.class; c = c.getSuperclass()) {
                if (declares(c, "m_21444_", BlockPos.class) || declares(c, "m_21536_")) return false;
            }
            for (Class<?> c = type; c != null && c != Entity.class; c = c.getSuperclass()) {
                if (declaresReturning(c, "m_9236_", Level.class) || declaresReturning(c, "m_20183_", BlockPos.class)) return false;
            }
            return true;
        }
    };

    /** Block classes that override Forge's canEntityDestroy (their positions always run the original test). */
    static final ClassValue<Boolean> DESTROY_HOOK = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            try {
                Method m = type.getMethod("canEntityDestroy", BlockState.class, BlockGetter.class, BlockPos.class, Entity.class);
                return m.getDeclaringClass() != IForgeBlock.class;
            } catch (Throwable t) {
                return true;
            }
        }
    };

    private TurtleEggSearch() {
    }

    static boolean declares(Class<?> c, String name, Class<?>... params) {
        try {
            c.getDeclaredMethod(name, params);
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        } catch (Throwable t) {
            return true;
        }
    }

    /** True when c declares a non-bridge, non-static method name() with exactly that return type. */
    static boolean declaresReturning(Class<?> c, String name, Class<?> returns) {
        try {
            for (Method m : c.getDeclaredMethods()) {
                if (m.getName().equals(name) && m.getParameterCount() == 0 && m.getReturnType() == returns && !m.isBridge()
                        && !Modifier.isStatic(m.getModifiers())) return true;
            }
            return false;
        } catch (Throwable t) {
            return true;
        }
    }

    private static boolean stateKeepsForgeDefault() {
        try {
            return BlockState.class.getMethod("canEntityDestroy", BlockGetter.class, BlockPos.class, Entity.class).getDeclaringClass() == IForgeBlockState.class;
        } catch (Throwable t) {
            return false;
        }
    }

    /** True when the switch may answer this goal's search; false means: run the original. */
    public static boolean eligible(MoveToBlockGoal goal, PathfinderMob mob) {
        return enabled && NonPoiSearch.READY && STATE_DEFAULT && mob != null && GOAL_OK.get(goal.getClass()) && MOB_OK.get(mob.getClass())
                && !(mob instanceof WitherBoss);
    }

    /**
     * The search. Returns the position the original would have stored in blockPos (the search's own MutableBlockPos), null
     * when it finds nothing, or {@link #FALLBACK} when the original must run instead (decided before any call is made).
     */
    public static BlockPos search(Goal goal, PathfinderMob mob, int range, int verticalRange, int verticalStart, Block target) {
        Level level = mob.m_9236_();
        if (level == null || level.m_46659_() || range > 128) return FALLBACK;          // debug worlds answer debug states
        int[] layers = NonPoiSearch.layers(verticalRange, verticalStart);
        if (layers == null) return FALLBACK;
        int[] ring = NonPoiSearch.ring(range);
        BlockPos center = mob.m_20183_();
        int cx = center.m_123341_(), cy = center.m_123342_(), cz = center.m_123343_();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        if (ring.length == 0 || layers.length == 0) return null;
        NonPoiSearch.Verdicts verdicts = verdicts(level, cx, cy, cz, range - 1, layers, target);
        boolean restricted = mob.m_21536_();
        boolean slow = false;                       // set when another mod's code moved the mob to another level
        long lastKey = NO_KEY;
        ChunkAccess chunk = null;
        int column = 0;
        for (int k : layers) {
            int y = cy + k - 1;
            int si = level.m_151564_(y);
            int sectionOffset = si - verdicts.secMin;
            for (int packed : ring) {
                int x = cx + (packed >> 16), z = cz + (short) packed;
                if (slow) {
                    // exactly the original's per-position code
                    cursor.m_122178_(x, y, z);
                    if (mob.m_21444_(cursor) && goal.bons$isValidTarget(mob.m_9236_(), cursor)) return cursor;
                    continue;
                }
                if (restricted && !mob.m_21444_(cursor.m_122178_(x, y, z))) continue;
                int chunkX = x >> 4, chunkZ = z >> 4;
                long key = (long) chunkX & 0xFFFFFFFFL | ((long) chunkZ & 0xFFFFFFFFL) << 32;
                if (key != lastKey) {
                    chunk = level.m_6522_(chunkX, chunkZ, ChunkStatus.f_62326_, false);    // the call isValidTarget makes
                    lastKey = key;
                    column = verdicts.column(chunkX, chunkZ);
                }
                if (chunk == null) continue;                                           // isValidTarget: false
                if (verdicts.verdict(chunk, column + sectionOffset, si) == NonPoiSearch.SKIP) continue;
                if (goal.bons$isValidTarget(level, cursor.m_122178_(x, y, z))) return cursor;
                // the original test may have run another mod's code: re-read what the next positions depend on
                lastKey = NO_KEY;
                restricted = mob.m_21536_();
                if (mob.m_9236_() != level) slow = true;
            }
        }
        return null;
    }

    static NonPoiSearch.Verdicts verdicts(Level level, int cx, int cy, int cz, int reach, int[] layers, Block target) {
        int minLayer = Integer.MAX_VALUE, maxLayer = Integer.MIN_VALUE;
        for (int k : layers) {
            minLayer = Math.min(minLayer, k - 1);
            maxLayer = Math.max(maxLayer, k - 1);
        }
        return new NonPoiSearch.Verdicts(SectionPos.m_123171_(cx - reach), SectionPos.m_123171_(cx + reach),
                SectionPos.m_123171_(cz - reach), SectionPos.m_123171_(cz + reach), level.m_151564_(cy + minLayer),
                level.m_151564_(cy + maxLayer), s -> s.m_60734_() == target || DESTROY_HOOK.get(s.m_60734_().getClass()),
                Blocks.f_50016_.m_49966_());
    }

    public static void announce() {
        if (!announced) {
            announced = true;
            NonPoiSearch.LOGGER.info("Bons and Furious: vanilla_turtle_egg_search_sections applies (RemoveBlockGoal searches skip block reads in sections that cannot hold the target){}",
                    SHADOW ? " - shadow verification on" : "");
        }
    }

    /** Shadow mode: the switch's answer against the original's. */
    public static void shadow(BlockPos ours, boolean originalFound, BlockPos originalPos) {
        SHADOW_CHECKS.incrementAndGet();
        boolean same = ours == null ? !originalFound : originalFound && originalPos != null && ours.equals(originalPos)
                && ours.getClass() == originalPos.getClass();
        if (same) return;
        long m = SHADOW_MISMATCHES.incrementAndGet();
        if (m <= 20) {
            NonPoiSearch.LOGGER.warn("Bons and Furious: vanilla_turtle_egg_search_sections shadow mismatch #{}: the switch found {} where the original found {}",
                    m, ours, originalFound ? originalPos : "nothing");
        }
    }
}
