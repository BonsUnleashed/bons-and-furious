package bons.furious.patch.dynamictrees_c2;

import com.ferreusveritas.dynamictrees.block.branch.BasicBranchBlock;
import com.ferreusveritas.dynamictrees.block.branch.BasicRootsBlock;
import com.ferreusveritas.dynamictrees.block.branch.BranchBlock;
import com.ferreusveritas.dynamictrees.systems.genfeature.GenFeature;
import com.ferreusveritas.dynamictrees.systems.genfeature.GenFeatureConfiguration;
import com.ferreusveritas.dynamictrees.systems.genfeature.MushroomRotGenFeature;
import com.ferreusveritas.dynamictrees.systems.genfeature.RotSoilGenFeature;
import com.ferreusveritas.dynamictrees.systems.genfeature.context.GenerationContext;
import com.ferreusveritas.dynamictrees.systems.genfeature.context.PostRotContext;
import com.ferreusveritas.dynamictrees.tree.species.Species;
import com.mojang.logging.LogUtils;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelAccessor;
import org.slf4j.Logger;

/**
 * Bons and Furious switch dynamictrees_rot_cycle_guard (Dynamic Trees 1.20.1-1.4.11, MIT; both sides, world generation).
 * A crash fix; no Dynamic Trees code is carried here.
 *
 * During world generation Dynamic Trees rots unsupported branches "rapidly": BasicBranchBlock.checkForRot (and
 * BasicRootsBlock.checkForRot for roots) calls Species.rot, which removes the branch (BranchBlock.breakDeliberate), runs the
 * POST_ROT features and reports true without checking the removal; then checkForRot recurses into every neighbour that is
 * the same block. A WorldGenRegion refuses writes outside its 3x3 writable chunks (log line "Detected setBlock in a far
 * chunk") while it still reads 8 chunks out, so a branch there stays where it is, is reported as rotted, and two such
 * neighbours recurse into each other until the worker thread dies with StackOverflowError (Dynamic Trees issue #1150;
 * its 1.4.11 fix only made the fluid read in breakDeliberate safe).
 *
 * What changes: a per-thread record of the rapid checkForRot calls currently on the stack that got past their support check
 * (position, radius, and how many of this thread's Dynamic Trees rot writes had succeeded at that moment), made where the
 * call reaches Species.rot and dropped at its final return. A rapid call that reaches Species.rot while a call for the same
 * position, block, level, species, fertility, radius and random source is on the stack, with no rot write succeeding since
 * that call reached Species.rot, does not call Species.rot: it answers "not rotted", so it returns false without recursing.
 * Before that point the call has only read blocks.
 *
 * Why every run that ends in the original is unchanged: in that situation nothing in the world has changed since the
 * earlier call got there (the only writes on this path are breakDeliberate's removal and the MushroomRot / RotSoil features'
 * placements, all observed here, and a write that reports failure changes nothing), and the code that ran in between makes
 * no decision that depends on anything else: MushroomRot draws from the random source only to choose which mushroom to try
 * at a position whose write is refused anyway. So the new call would repeat the earlier one step for step, reach this
 * position again and never return: the original overflows the stack in exactly these runs. Before the first cut the two
 * versions execute the same instructions in the same order (this class only reads its own bookkeeping), so a run in which
 * the original terminates never reaches a cut and stays identical: same removals, POST_ROT writes, log lines, random draws
 * and return values. With the cut, a cascade always ends, since only finitely many writes can succeed. The hooks sit at
 * the Species.rot call and the final return (not HEAD and every RETURN), so checkForRot stays small enough for the JIT to
 * inline its recursion as before: a long finite cascade overflows at the same depth as in the original.
 *
 * The argument covers Dynamic Trees' own code, so a cut is made only when the species, its POST_ROT features and the
 * branch block are Dynamic Trees 1.4.11's own implementations of the methods involved (checked by reflection, once per
 * class) and, for branches, fertility is not positive (Species.rot's leaf-growing branch reads light). Otherwise the
 * original runs (WARN once). Remaining assumption, true for every implementation in Dynamic Trees 1.4.11: a TreePart's
 * branchSupport answer depends only on its arguments. A thread's record lives only while its cascade runs.
 */
public final class RotCycleGuard {
    /** Runtime switch (the config switch acts when classes are transformed). -Dbons_and_furious.dynamictreesRotCycleGuard=false turns it off. */
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.dynamictreesRotCycleGuard", "true"));
    /** Calls cut so far, and cycles left to the original code because foreign code was involved (tests and the log). */
    public static final AtomicLong CUTS = new AtomicLong();
    public static final AtomicLong FOREIGN = new AtomicLong();
    private static final Logger LOGGER = LogUtils.getLogger();
    private static volatile boolean announced, warned;
    private static final ThreadLocal<Cascade> CASCADE = ThreadLocal.withInitial(Cascade::new);

    private RotCycleGuard() {
    }

    /** One thread's recorded rapid-rot calls; all of them share the bottom call's level, block, species, fertility and random. */
    static final class Cascade {
        long writes;                      // successful Dynamic Trees rot writes on this thread so far (only ever grows)
        int depth;
        Object level, block, species, rand;
        int fertility;
        long[] pos = new long[32];
        int[] radius = new int[32];
        long[] start = new long[32];
    }

    /**
     * The Species.rot call of BasicBranchBlock / BasicRootsBlock.checkForRot, reached after the call's support reads. For a
     * rapid call either the cut (Species.rot is not called; didRot = false, so checkForRot returns false without recursing),
     * or the call is recorded until {@link #exit} and Species.rot runs as before. Any other call runs Species.rot as before.
     */
    public static boolean cut(Object block, Species species, LevelAccessor level, BlockPos pos, int radius, int fertility, RandomSource rand,
                              boolean rotRapid, boolean roots) {
        // Nine arguments, like checkForRot's own calls, so the hook does not widen checkForRot's stack frame. Branches pass
        // their own rapid flag to Species.rot; roots always pass true and are recorded either way (a root call that is not
        // rapid never recurses, so its record is only ever the bottom one and is dropped at its return).
        return rotRapid && enabled && cutOrRecord(block, level, pos, species, fertility, radius, rand, roots);
    }

    /** Species.handleRot, Dynamic Trees' only caller of checkForRot, is never inside a cascade: anything recorded is left over. */
    public static void outermost() {
        Cascade c = CASCADE.get();
        if (c.depth != 0) {
            c.depth = 0;
            c.level = c.block = c.species = c.rand = null;
        }
    }

    /** True: cut. Otherwise records the rapid call (the newest record on this thread). */
    static boolean cutOrRecord(Object block, LevelAccessor level, BlockPos pos, Species species, int fertility, int radius,
                               RandomSource rand, boolean roots) {
        Cascade c = CASCADE.get();
        int d = c.depth;
        long p = pos.m_121878_();
        if (d > 0 && !(c.level == level && c.block == block && c.species == species && c.fertility == fertility && c.rand == rand
                && adjacent(c.pos[d - 1], p))) {
            // Not the recursion of the record on top (a call from elsewhere, or a record left by an exception): start afresh.
            // Forgetting records only means fewer cuts, never a wrong one.
            d = 0;
            c.depth = 0;
        }
        if (d == 0) {
            c.level = level;
            c.block = block;
            c.species = species;
            c.fertility = fertility;
            c.rand = rand;
        } else {
            for (int i = d - 1; i >= 0; i--) {
                if (c.pos[i] != p) {
                    continue;
                }
                // the newest record for this position has seen the fewest writes since
                if (c.radius[i] == radius && c.start[i] == c.writes) {
                    if ((roots || fertility <= 0) && ownCode(block, species)) {
                        announceCut(pos);
                        return true;
                    }
                    foreign(pos, block, species);
                }
                break;
            }
        }
        if (d == c.pos.length) {
            c.pos = java.util.Arrays.copyOf(c.pos, d * 2);
            c.radius = java.util.Arrays.copyOf(c.radius, d * 2);
            c.start = java.util.Arrays.copyOf(c.start, d * 2);
        }
        c.pos[d] = p;
        c.radius[d] = radius;
        c.start[d] = c.writes;
        c.depth = d + 1;
        return false;
    }

    /**
     * The final return of checkForRot (the only exit after its Species.rot call): drops the top record when it is this call's
     * (same position, block and level). Nested calls have already dropped theirs; a call that was not recorded finds a
     * neighbour's record on top (or none) and leaves it. A record left behind by an exception only ever means fewer cuts:
     * the next call that is not its recursion starts afresh.
     */
    public static boolean exit(Object block, LevelAccessor level, BlockPos pos, boolean didRot) {
        Cascade c = CASCADE.get();
        int d = c.depth;
        if (d > 0 && c.pos[d - 1] == pos.m_121878_() && c.level == level && c.block == block) {
            c.depth = --d;
            if (d == 0) {
                c.level = c.block = c.species = c.rand = null;
            }
        }
        return didRot;
    }

    /** The result of one of the rot path's writes (breakDeliberate, MushroomRot, RotSoil); passed through unchanged. */
    public static boolean wrote(boolean success) {
        if (success) {
            CASCADE.get().writes++;
        }
        return success;
    }

    private static boolean adjacent(long a, long b) {
        int dx = Math.abs(BlockPos.m_121983_(a) - BlockPos.m_121983_(b));
        int dy = Math.abs(BlockPos.m_122008_(a) - BlockPos.m_122008_(b));
        int dz = Math.abs(BlockPos.m_122015_(a) - BlockPos.m_122015_(b));
        return dx + dy + dz == 1;
    }

    private static void announceCut(BlockPos pos) {
        long n = CUTS.incrementAndGet();
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: dynamictrees_rot_cycle_guard: stopped an endless rapid-rot loop at {} (a branch the "
                    + "generation region does not let Dynamic Trees remove); later stops are logged at DEBUG", pos);
        } else if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Bons and Furious: dynamictrees_rot_cycle_guard: stopped rapid-rot loop #{} at {}", n, pos);
        }
    }

    private static void foreign(BlockPos pos, Object block, Species species) {
        FOREIGN.incrementAndGet();
        if (!warned) {
            warned = true;
            LOGGER.warn("Bons and Furious: dynamictrees_rot_cycle_guard: the rapid-rot loop at {} ({} / {}) runs code that does not come "
                    + "with Dynamic Trees 1.4.11 (or a positive fertility); it is left to the original code", pos,
                    block.getClass().getName(), species.getClass().getName());
        }
    }

    // ---- Is the code between two calls Dynamic Trees 1.4.11's own? (once per class) ----

    private static final ClassValue<Boolean> BLOCK_OK = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> c) {
            try {
                Class<?> check = declarer(c, "checkForRot", LevelAccessor.class, BlockPos.class, Species.class, int.class, int.class,
                        RandomSource.class, float.class, boolean.class);
                return (check == BasicBranchBlock.class || check == BasicRootsBlock.class)
                        && declarer(c, "rot", LevelAccessor.class, BlockPos.class) == BranchBlock.class
                        && declarer(c, "breakDeliberate", LevelAccessor.class, BlockPos.class,
                        com.ferreusveritas.dynamictrees.DynamicTrees.DestroyMode.class) == BranchBlock.class;
            } catch (Throwable t) {
                // 1.0.34: getDeclaredMethod resolves the types of every method the class declares; one that names a class
                // missing on this side (NoClassDefFoundError on a dedicated server) would have escaped from the rot path.
                // Such a class is not proven to be Dynamic Trees' own code: the original runs.
                return false;
            }
        }
    };
    private static final ClassValue<Boolean> SPECIES_OK = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> c) {
            try {
                return declarer(c, "rot", LevelAccessor.class, BlockPos.class, int.class, int.class, int.class, RandomSource.class,
                        boolean.class, boolean.class) == Species.class
                        && declarer(c, "postRot", PostRotContext.class) == Species.class
                        && declarer(c, "getGenFeatures") == Species.class;
            } catch (Throwable t) {
                return false;   // 1.0.34: see BLOCK_OK
            }
        }
    };
    private static final Set<Class<?>> OWN_POST_ROT = Set.of(GenFeature.class, MushroomRotGenFeature.class, RotSoilGenFeature.class);
    private static final ClassValue<Boolean> FEATURE_OK = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> c) {
            try {
                return declarer(c, "generate", GenFeatureConfiguration.class, GenFeature.Type.class, GenerationContext.class) == GenFeature.class
                        && OWN_POST_ROT.contains(declarer(c, "postRot", GenFeatureConfiguration.class, PostRotContext.class));
            } catch (Throwable t) {
                return false;   // 1.0.34: see BLOCK_OK (also a null declarer, which Set.contains refuses)
            }
        }
    };

    static boolean ownCode(Object block, Species species) {
        if (!BLOCK_OK.get(block.getClass()) || !SPECIES_OK.get(species.getClass())) {
            return false;
        }
        List<GenFeatureConfiguration> features = species.getGenFeatures();
        for (int i = 0; i < features.size(); i++) {
            GenFeatureConfiguration cfg = features.get(i);
            if (cfg.getClass() != GenFeatureConfiguration.class || !FEATURE_OK.get(cfg.getGenFeature().getClass())) {
                return false;
            }
        }
        return true;
    }

    /** The class that declares the method a virtual call on an instance of c would run (null if none). */
    static Class<?> declarer(Class<?> c, String name, Class<?>... params) {
        for (Class<?> k = c; k != null; k = k.getSuperclass()) {
            try {
                Method m = k.getDeclaredMethod(name, params);
                return m.getDeclaringClass();
            } catch (NoSuchMethodException e) {
                // keep looking in the superclass
            }
        }
        return null;
    }
}
