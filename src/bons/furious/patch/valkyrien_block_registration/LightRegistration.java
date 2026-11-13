package bons.furious.patch.valkyrien_block_registration;

import bons.furious.mixin.valkyrien_block_registration.BlockBehaviourPropertiesAccessor;
import bons.furious.mixin.valkyrien_block_registration.PropertiesSolidAccessor;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.logging.LogUtils;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.slf4j.Logger;

/**
 * Bons and Furious switch valkyrien_block_registration_lazy_cache (Valkyrien Skies 2.4.11 next to ModernFix's lazy
 * block-state cache). SRG member names.
 *
 * When a world starts, Valkyrien Skies registers every block state of the game
 * (MassDatapackResolver.registerAllBlockStates): for a state that is neither air nor a liquid it asks isSolid()
 * (m_280296_) and then getShape(getter, pos) for a solid state or getCollisionShape(getter, pos) (m_60812_) for any
 * other. isSolid() reads the field legacySolid and the two-argument getCollisionShape reads the field cache; ModernFix
 * builds a state's cache only when one of those fields is first read, so these two calls build the whole cache
 * (occlusion shapes, face tests, light values, ...) of every state in the pack at every world start.
 *
 * Both answers depend on one thing the cache holds, the collision shape, and vanilla computes it with fixed arguments
 * (BlockStateBase.initCache m_60611_, Cache.<init>, calculateSolid m_280210_):
 *   legacySolid = forceSolidOn ? true : forceSolidOff ? false : cache == null ? false
 *                 : shape.isEmpty() ? false : bounds.getSize() >= 0.7291666666666666 || bounds.getYsize() >= 1.0
 *   cache == null exactly when the block has a dynamic shape (hasDynamicShape m_49967_)
 *   cache.collisionShape = block.getCollisionShape(state, EmptyBlockGetter.INSTANCE, BlockPos.ZERO, CollisionContext.empty())
 *   getCollisionShape(getter, pos) = cache != null ? cache.collisionShape : getCollisionShape(getter, pos, CollisionContext.empty())
 * For a state whose cache ModernFix has not built yet, the two wrappers compute exactly that and leave the cache
 * unbuilt; the collision shape computed for isSolid() is handed to the getCollisionShape call that follows for the
 * same state.
 *
 * The block's collision shape is asked the way ModernFix's cache build asks it. ModernFix builds one cache at a time
 * under the lock of BlockStateBase.class with a flag set (its buildingCache), and while that flag is set a read of any
 * other pending state answers from that state's present fields without building it. A block whose shape looks at
 * another state therefore sees the same thing here as inside the build: this code first lets the pending fluid state
 * be computed (initCache's first step), then takes the same lock, sets the same flag for the one call and clears it.
 * A state that is built meanwhile, a call made from inside a cache build, a game without ModernFix's lazy cache or
 * without that flag, and a state whose cache constructor would throw (a non-empty collision shape together with an
 * offset function) all run the original call, so that error still appears where it did.
 *
 * What differs (variant): the cache of a state that nothing else asks for is not built at world start; it is built,
 * by ModernFix as before, at the first other read.
 *
 * -Dbons_and_furious.vsRegistration=false keeps the original calls; .verify=true asks the original after each computed
 * answer and compares (rigs: it builds every cache again); .metrics=true prints a summary when the JVM exits, with a
 * digest of every answer given in registerAllBlockStates (compare a run with the switch off: the digests are equal
 * when every state got the same two answers).
 */
public final class LightRegistration {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final boolean ACTIVE = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.vsRegistration", "true"));
    static final boolean VERIFY = Boolean.getBoolean("bons_and_furious.vsRegistration.verify");
    static final boolean METRICS = VERIFY || Boolean.getBoolean("bons_and_furious.vsRegistration.metrics");
    public static final LongAdder COMPUTED_SOLID = new LongAdder(), COMPUTED_SHAPE = new LongAdder(), ORIGINAL = new LongAdder(),
            COMPARED = new LongAdder(), MISMATCHED = new LongAdder();
    /** Sum over every answer of a hash of (state, answer): the same in two runs that answered every state alike. */
    private static final AtomicLong DIGEST = new AtomicLong();
    /** ModernFix's "a cache is being built" flag on BlockStateBase (a static boolean its mixin adds), or null. */
    private static final VarHandle BUILDING = buildingFlag();
    /** True when ModernFix's lazy block-state cache is present (its duck interface is on BlockState) and its flag was found. */
    private static final boolean LAZY = lazyCachePresent();
    /** The state the last isSolid() was computed for on this thread, and its collision shape. */
    private static final ThreadLocal<Object[]> LAST = ThreadLocal.withInitial(() -> new Object[2]);

    static {
        if (METRICS) {
            Runtime.getRuntime().addShutdownHook(new Thread(() -> System.out.println("Bons and Furious valkyrien_block_registration_lazy_cache: isSolid computed "
                    + COMPUTED_SOLID.sum() + ", collision shapes handed over " + COMPUTED_SHAPE.sum() + ", original calls " + ORIGINAL.sum() + ", compared "
                    + COMPARED.sum() + ", differences " + MISMATCHED.sum() + ", digest of all answers " + Long.toHexString(DIGEST.get())),
                    "Bons and Furious VS registration summary"));
        }
    }

    private LightRegistration() {
    }

    private static VarHandle buildingFlag() {
        try {
            Class<?> base = BlockBehaviour.BlockStateBase.class;
            return MethodHandles.privateLookupIn(base, MethodHandles.lookup()).findStaticVarHandle(base, "buildingCache", boolean.class);
        } catch (Throwable t) {
            return null;
        }
    }

    private static boolean lazyCachePresent() {
        try {
            Class<?> duck = Class.forName("org.embeddedt.modernfix.duck.IBlockState", false, LightRegistration.class.getClassLoader());
            if (!duck.isAssignableFrom(BlockState.class)) return false;
            if (BUILDING == null) {
                LOGGER.info("Bons and Furious: valkyrien_block_registration_lazy_cache keeps the original calls: ModernFix's lazy block-state "
                        + "cache is here, but not the flag its cache build sets (another ModernFix build)");
                return false;
            }
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Kept apart so the ModernFix interface is only linked when it exists. */
    private static final class Duck {
        static boolean unbuilt(BlockState state) {
            return ((org.embeddedt.modernfix.duck.IBlockState) state).isCacheInvalid();
        }
    }

    /**
     * What a cache build would store as the state's collision shape, asked under ModernFix's lock with its flag set, as
     * the build does. Null when the original call has to decide: the state was built meanwhile, a cache build is running
     * on this thread, or the cache constructor would throw for this state.
     */
    private static VoxelShape cacheCollisionShape(BlockState state, Block block) {
        state.m_60819_();                                    // getFluidState(): a pending fluid state is computed, initCache's first step
        synchronized (BlockBehaviour.BlockStateBase.class) {
            if (!Duck.unbuilt(state) || (boolean) BUILDING.get()) return null;
            BUILDING.set(true);
            try {
                VoxelShape shape = block.m_5939_(state, EmptyBlockGetter.INSTANCE, BlockPos.f_121853_, CollisionContext.m_82749_());
                return !shape.m_83281_() && state.m_271730_() ? null : shape;
            } finally {
                BUILDING.set(false);
            }
        }
    }

    private static void digest(BlockState state, long answer) {
        long h = (state.toString().hashCode() * 0x9E3779B97F4A7C15L) ^ answer;
        h ^= h >>> 32;
        h *= 0xD6E8FEB86659FD93L;
        h ^= h >>> 32;
        DIGEST.addAndGet(h);
    }

    private static long shapeHash(VoxelShape shape) {
        long h = 0x51ED270B0F1A2C3DL;
        for (AABB box : shape.m_83299_()) {
            for (double d : new double[] {box.f_82288_, box.f_82289_, box.f_82290_, box.f_82291_, box.f_82292_, box.f_82293_}) {
                h = (h ^ Double.doubleToLongBits(d)) * 0x100000001B3L;
            }
        }
        return h;
    }

    /** MassDatapackResolver.registerAllBlockStates: state.isSolid(). */
    public static boolean isSolid(BlockState state, Operation<Boolean> original) {
        boolean solid = isSolid0(state, original);
        if (METRICS) digest(state, solid ? 1L : 2L);
        return solid;
    }

    private static boolean isSolid0(BlockState state, Operation<Boolean> original) {
        if (!ACTIVE || !LAZY || !Duck.unbuilt(state)) {
            if (METRICS) ORIGINAL.increment();
            return original.call(state);
        }
        Block block = state.m_60734_();
        Object props = ((BlockBehaviourPropertiesAccessor) block).bons$properties();
        boolean solid;
        VoxelShape shape = null;
        if (((PropertiesSolidAccessor) props).bons$forceSolidOn()) {
            solid = true;
        } else if (((PropertiesSolidAccessor) props).bons$forceSolidOff()) {
            solid = false;
        } else if (block.m_49967_()) {
            solid = false;                                   // dynamic shape: no cache
        } else {
            shape = cacheCollisionShape(state, block);
            if (shape == null) {
                if (METRICS) ORIGINAL.increment();
                return original.call(state);
            }
            if (shape.m_83281_()) {
                solid = false;
            } else {
                AABB box = shape.m_83215_();
                solid = box.m_82309_() >= 0.7291666666666666D || box.m_82376_() >= 1.0D;
            }
        }
        Object[] last = LAST.get();
        last[0] = state;
        last[1] = shape;
        if (METRICS) COMPUTED_SOLID.increment();
        if (VERIFY) {
            boolean want = original.call(state);             // builds the cache
            COMPARED.increment();
            if (want != solid) {
                MISMATCHED.increment();
                LOGGER.warn("Bons and Furious: valkyrien_block_registration_lazy_cache computed isSolid {} for {}, the game says {}", solid, state, want);
                return want;
            }
            if (shape != null) {                             // the cache exists now: its collision shape against the computed one
                VoxelShape cached = state.m_60812_(EmptyBlockGetter.INSTANCE, BlockPos.f_121853_);
                COMPARED.increment();
                if (cached != shape && Shapes.m_83157_(cached, shape, BooleanOp.f_82687_)) {
                    MISMATCHED.increment();
                    LOGGER.warn("Bons and Furious: valkyrien_block_registration_lazy_cache computed another collision shape for {} than the game", state);
                }
            }
        }
        return solid;
    }

    /** MassDatapackResolver.registerAllBlockStates: state.getCollisionShape(getter, pos). */
    public static VoxelShape collisionShape(BlockState state, BlockGetter getter, BlockPos pos, Operation<VoxelShape> original) {
        VoxelShape shape = collisionShape0(state, getter, pos, original);
        if (METRICS) digest(state, shapeHash(shape));
        return shape;
    }

    private static VoxelShape collisionShape0(BlockState state, BlockGetter getter, BlockPos pos, Operation<VoxelShape> original) {
        if (!ACTIVE || !LAZY || !Duck.unbuilt(state)) {
            if (METRICS) ORIGINAL.increment();
            return original.call(state, getter, pos);
        }
        Block block = state.m_60734_();
        VoxelShape shape;
        if (block.m_49967_()) {
            shape = state.m_60742_(getter, pos, CollisionContext.m_82749_());      // no cache: the three-argument form, as the original takes
        } else {
            Object[] last = LAST.get();
            shape = last[0] == state && last[1] != null ? (VoxelShape) last[1] : cacheCollisionShape(state, block);
            last[0] = null;
            last[1] = null;
            if (shape == null) {
                if (METRICS) ORIGINAL.increment();
                return original.call(state, getter, pos);
            }
        }
        if (METRICS) COMPUTED_SHAPE.increment();
        if (VERIFY) {
            VoxelShape want = original.call(state, getter, pos);
            COMPARED.increment();
            if (want != shape && Shapes.m_83157_(want, shape, BooleanOp.f_82687_)) {
                MISMATCHED.increment();
                LOGGER.warn("Bons and Furious: valkyrien_block_registration_lazy_cache computed another collision shape for {} than the game", state);
            }
            return want;
        }
        return shape;
    }
}
