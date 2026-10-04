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
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.neoforged.neoforge.common.extensions.IBlockExtension;
import net.neoforged.neoforge.common.extensions.IBlockStateExtension;

/**
 * Bons and Furious switch vanilla_turtle_egg_search_sections (Minecraft 1.21.1 with NeoForge 21.1.252; server side,
 * including the integrated server). Mojang member names.
 *
 * Zombies, husks, drowned, zombie villagers and zombified piglins (and every mod goal built on RemoveBlockGoal) search for
 * their target block every 5-10 seconds: MoveToBlockGoal.findNearestBlock visits 7 layers of 47 x 47 positions around the
 * mob and asks RemoveBlockGoal.isValidTarget at each: getChunk(x, z, FULL, false), then the block state twice
 * (canEntityDestroy, then is(target) and two air checks above). That is about 15,500 positions per attempt.
 *
 * The switch visits the same positions in the same order and makes the same getChunk calls (only a call that repeats the
 * previous call with nothing in between is dropped: that call can only hit getChunk's cache), but it does not read the
 * block of a position whose chunk section cannot hold an interesting state: one that is the target block, or whose block
 * class overrides NeoForge's canEntityDestroy. For every other state isValidTarget is a pure false: NeoForge's default
 * canEntityDestroy (IBlockExtension) returns true without side effects for an entity that is not an ender dragon or
 * wither, and is(target) is false. Positions in sections that may hold an interesting state run the real isValidTarget.
 *
 * Applies only where the result is provably the same (decided once per class and cached by class):
 *  - the goal is a RemoveBlockGoal whose class, up to RemoveBlockGoal, declares neither isValidTarget nor
 *    findNearestBlock; every other MoveToBlockGoal (cats, foxes, striders, mod goals that override them, ...) runs
 *    unchanged;
 *  - the mob's class keeps Mob.isWithinRestriction and Mob.hasRestriction, Entity.level and Entity.blockPosition, and the
 *    mob is not a wither (whose canEntityDestroy default consults NeoForge's overridable isAir; ender dragons and wither
 *    skulls are no PathfinderMobs);
 *  - BlockState keeps IBlockStateExtension's canEntityDestroy; the level is not a debug world.
 * If another mod's code, run by an original test, moves the mob to another level, the rest of that search runs the
 * original per-position code. Assumption (true for vanilla 1.21.1; with Radium 0.13.1's chunk_access the shadow rig is
 * the check): a getChunk call that repeats the previous one with nothing in between has no effect beyond returning the
 * same chunk.
 *
 * Ported to 1.21.1: NeoForge renamed Forge's IForgeBlock / IForgeBlockState to IBlockExtension / IBlockStateExtension;
 * canEntityDestroy keeps its name, descriptor and default body (dragon: DRAGON_IMMUNE tag; wither boss / wither skull:
 * isAir or WitherBoss.canDestroy; everyone else: true), so the override scan and the BlockState check only name the new
 * interfaces. ChunkStatus moved to net.minecraft.world.level.chunk.status. MoveToBlockGoal.findNearestBlock,
 * RemoveBlockGoal.isValidTarget (now inside one ternary, same calls in the same order) and Mob's restriction methods are
 * unchanged.
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
    private static final boolean STATE_DEFAULT = stateKeepsNeoForgeDefault();

    /** The goal side (MoveToBlockGoalSearchMixin): vanilla's own isValidTarget. */
    public interface Goal {
        boolean bons$isValidTarget(LevelReader level, BlockPos pos);
    }

    private static final ClassValue<Boolean> GOAL_OK = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            if (!RemoveBlockGoal.class.isAssignableFrom(type)) return false;
            for (Class<?> c = type; c != RemoveBlockGoal.class; c = c.getSuperclass()) {
                if (declares(c, "isValidTarget", LevelReader.class, BlockPos.class) || declares(c, "findNearestBlock")) return false;
            }
            return true;
        }
    };

    private static final ClassValue<Boolean> MOB_OK = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            for (Class<?> c = type; c != null && c != Mob.class; c = c.getSuperclass()) {
                if (declares(c, "isWithinRestriction", BlockPos.class) || declares(c, "hasRestriction")) return false;
            }
            for (Class<?> c = type; c != null && c != Entity.class; c = c.getSuperclass()) {
                if (declaresReturning(c, "level", Level.class) || declaresReturning(c, "blockPosition", BlockPos.class)) return false;
            }
            return true;
        }
    };

    /** Block classes that override NeoForge's canEntityDestroy (their positions always run the original test). */
    static final ClassValue<Boolean> DESTROY_HOOK = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            try {
                Method m = type.getMethod("canEntityDestroy", BlockState.class, BlockGetter.class, BlockPos.class, Entity.class);
                return m.getDeclaringClass() != IBlockExtension.class;
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

    private static boolean stateKeepsNeoForgeDefault() {
        try {
            return BlockState.class.getMethod("canEntityDestroy", BlockGetter.class, BlockPos.class, Entity.class).getDeclaringClass() == IBlockStateExtension.class;
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
        Level level = mob.level();
        if (level == null || level.isDebug() || range > 128) return FALLBACK;          // debug worlds answer debug states
        int[] layers = NonPoiSearch.layers(verticalRange, verticalStart);
        if (layers == null) return FALLBACK;
        int[] ring = NonPoiSearch.ring(range);
        BlockPos center = mob.blockPosition();
        int cx = center.getX(), cy = center.getY(), cz = center.getZ();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        if (ring.length == 0 || layers.length == 0) return null;
        NonPoiSearch.Verdicts verdicts = verdicts(level, cx, cy, cz, range - 1, layers, target);
        boolean restricted = mob.hasRestriction();
        boolean slow = false;                       // set when another mod's code moved the mob to another level
        long lastKey = NO_KEY;
        ChunkAccess chunk = null;
        int column = 0;
        for (int k : layers) {
            int y = cy + k - 1;
            int si = level.getSectionIndex(y);
            int sectionOffset = si - verdicts.secMin;
            for (int packed : ring) {
                int x = cx + (packed >> 16), z = cz + (short) packed;
                if (slow) {
                    // exactly the original's per-position code
                    cursor.set(x, y, z);
                    if (mob.isWithinRestriction(cursor) && goal.bons$isValidTarget(mob.level(), cursor)) return cursor;
                    continue;
                }
                if (restricted && !mob.isWithinRestriction(cursor.set(x, y, z))) continue;
                int chunkX = x >> 4, chunkZ = z >> 4;
                long key = (long) chunkX & 0xFFFFFFFFL | ((long) chunkZ & 0xFFFFFFFFL) << 32;
                if (key != lastKey) {
                    chunk = level.getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);    // the call isValidTarget makes
                    lastKey = key;
                    column = verdicts.column(chunkX, chunkZ);
                }
                if (chunk == null) continue;                                           // isValidTarget: false
                if (verdicts.verdict(chunk, column + sectionOffset, si) == NonPoiSearch.SKIP) continue;
                if (goal.bons$isValidTarget(level, cursor.set(x, y, z))) return cursor;
                // the original test may have run another mod's code: re-read what the next positions depend on
                lastKey = NO_KEY;
                restricted = mob.hasRestriction();
                if (mob.level() != level) slow = true;
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
        return new NonPoiSearch.Verdicts(SectionPos.blockToSectionCoord(cx - reach), SectionPos.blockToSectionCoord(cx + reach),
                SectionPos.blockToSectionCoord(cz - reach), SectionPos.blockToSectionCoord(cz + reach), level.getSectionIndex(cy + minLayer),
                level.getSectionIndex(cy + maxLayer), s -> s.getBlock() == target || DESTROY_HOOK.get(s.getBlock().getClass()),
                Blocks.AIR.defaultBlockState());
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
