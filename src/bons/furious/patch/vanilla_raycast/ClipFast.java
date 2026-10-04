package bons.furious.patch.vanilla_raycast;

import bons.furious.mixin.vanilla_raycast.ClipContextAccessor;
import com.mojang.logging.LogUtils;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.slf4j.Logger;

/**
 * Bons and Furious switch vanilla_raycast_fluid_none (Minecraft 1.20.1 on Forge 47.4.16; both sides). SRG names.
 *
 * Every step of a raycast (BlockGetter.clip) reads the fluid state of the block it crosses, even when the clip context
 * ignores fluids (ClipContext.Fluid.NONE: mobs' line-of-sight checks, projectile steps, explosion exposure, block picking
 * with fluids off). For NONE, getFluidShape answers Shapes.empty() whatever the fluid state is, and the empty shape's clip
 * is null, so the read decides nothing. On a Level that read is a chunk lookup repeating the block read's lookup of the
 * same position (a chunk-cache hit) plus a section read: nothing observable.
 *
 * LevelClipMixin gives Level its own clip; for a plain ClipContext (exactly that class) with Fluid.NONE, on a level whose
 * class keeps Level.getFluidState, it runs vanilla's own traversal (BlockGetter.traverseBlocks) with a step that makes
 * every call of the original step, in the same order (getBlockState, the context's block shape, clipWithInteractionOverride,
 * the context's fluid shape, the fluid shape's clip, the two distances) except the fluid read; the fluid shape is asked
 * for the empty fluid state, which NONE never looks at. The miss result is built the same way. Every other clip runs
 * BlockGetter's default method.
 *
 * -Dbons_and_furious.raycastFluidNone=false runs the default method for every clip.
 * -Dbons_and_furious.raycastFluidNone.shadow=true (verification) runs both and compares the hit results (type, location,
 * face, block, inside); the default method's answer is used (SHADOW_CHECKS / SHADOW_MISMATCHES, first 20 logged).
 */
public final class ClipFast {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.raycastFluidNone", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.raycastFluidNone.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    private static volatile FluidState empty;
    private static volatile boolean announced;
    private static final ClassValue<Boolean> LEVEL_OK = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            if (!Level.class.isAssignableFrom(type)) return false;
            for (Class<?> c = type; c != Level.class; c = c.getSuperclass()) {
                try {
                    c.getDeclaredMethod("m_6425_", BlockPos.class);
                    return false;
                } catch (NoSuchMethodException e) {
                    // keeps Level's getFluidState
                } catch (Throwable t) {
                    return false;
                }
            }
            return true;
        }
    };

    private ClipFast() {
    }

    /** True when the clip may skip the fluid reads: a plain ClipContext with Fluid.NONE, a level that keeps getFluidState. */
    public static boolean applies(Object level, ClipContext context) {
        return context != null && context.getClass() == ClipContext.class && ((ClipContextAccessor) (Object) context).bons$fluid() == ClipContext.Fluid.NONE
                && LEVEL_OK.get(level.getClass());
    }

    public static BlockHitResult clip(BlockGetter level, ClipContext context) {
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: vanilla_raycast_fluid_none applies (raycasts that ignore fluids skip the fluid reads){}",
                    SHADOW ? " - shadow verification on" : "");
        }
        return BlockGetter.m_151361_(context.m_45702_(), context.m_45693_(), context, (c, pos) -> step(level, c, pos), ClipFast::miss);
    }

    /** One step of the ray, as the original step but without the fluid read (Fluid.NONE ignores it). */
    static BlockHitResult step(BlockGetter level, ClipContext c, BlockPos pos) {
        BlockState state = level.m_8055_(pos);
        Vec3 from = c.m_45702_();
        Vec3 to = c.m_45693_();
        VoxelShape shape = c.m_45694_(state, level, pos);
        BlockHitResult hit = level.m_45558_(from, to, pos, shape, state);
        VoxelShape fluidShape = c.m_45698_(emptyFluid(), level, pos);
        BlockHitResult fluidHit = fluidShape.m_83220_(from, to, pos);
        double d0 = hit == null ? Double.MAX_VALUE : c.m_45702_().m_82557_(hit.m_82450_());
        double d1 = fluidHit == null ? Double.MAX_VALUE : c.m_45702_().m_82557_(fluidHit.m_82450_());
        return d0 <= d1 ? hit : fluidHit;
    }

    static BlockHitResult miss(ClipContext c) {
        Vec3 back = c.m_45702_().m_82546_(c.m_45693_());
        return BlockHitResult.m_82426_(c.m_45693_(), Direction.m_122366_(back.f_82479_, back.f_82480_, back.f_82481_), BlockPos.m_274446_(c.m_45693_()));
    }

    private static FluidState emptyFluid() {
        FluidState e = empty;
        if (e == null) empty = e = Fluids.f_76191_.m_76145_();
        return e;
    }

    /** Rethrows t unchanged (checked or not), as the method handle's caller would have seen it. */
    @SuppressWarnings("unchecked")
    public static <T extends Throwable> RuntimeException rethrow(Throwable t) throws T {
        throw (T) t;
    }

    public static void shadow(BlockHitResult ours, BlockHitResult theirs) {
        SHADOW_CHECKS.incrementAndGet();
        if (key(ours).equals(key(theirs))) return;
        long m = SHADOW_MISMATCHES.incrementAndGet();
        if (m <= 20) LOGGER.warn("Bons and Furious: vanilla_raycast_fluid_none shadow mismatch #{}: {} where the original answered {}", m, key(ours), key(theirs));
    }

    static String key(BlockHitResult h) {
        return h == null ? "null" : h.m_6662_() + " " + h.m_82450_() + " " + h.m_82434_() + " " + h.m_82425_() + " " + h.m_82436_();
    }
}
