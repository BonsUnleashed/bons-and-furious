package bons.furious.patch.vanilla_raycast;

import bons.furious.mixin.vanilla_raycast.ClipContextAccessor;
import com.mojang.logging.LogUtils;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
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
 * Bons and Furious switch vanilla_raycast_fluid_none (Minecraft 1.21.1 with NeoForge 21.1.252; both sides).
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
 *
 * Ported to 1.21.1: the step, the miss lambda, traverseBlocks, getFluidShape and Fluid.NONE are unchanged. Generic
 * stand-down widened: besides a mod that adds clip to Level (LevelClipMixin's @Intrinsic keeps theirs), a mod can now
 * replace the clip every Level inherits by an @Overwrite of BlockGetter's default method (Radium 0.13.1's world.raycast,
 * on by default). OTHER_CLIP (read once from the loaded BlockGetter) names the first other mixin merged into BlockGetter's
 * clip, its lambdas, traverseBlocks or clipWithInteractionOverride (@MixinMerged), or any injector handler merged into
 * BlockGetter; when there is one, applies() is false for every clip and Level's clip is BlockGetter's (modified) method.
 */
public final class ClipFast {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.raycastFluidNone", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.raycastFluidNone.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    /** The other mixin that changes BlockGetter's clip path ("" = none); read once from the loaded (transformed) class. */
    public static final String OTHER_CLIP = otherClipMixin();
    private static volatile FluidState empty;
    private static volatile boolean announced;
    private static final ClassValue<Boolean> LEVEL_OK = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            if (!Level.class.isAssignableFrom(type)) return false;
            for (Class<?> c = type; c != Level.class; c = c.getSuperclass()) {
                try {
                    c.getDeclaredMethod("getFluidState", BlockPos.class);
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
        return OTHER_CLIP.isEmpty() && context != null && context.getClass() == ClipContext.class
                && ((ClipContextAccessor) (Object) context).bons$fluid() == ClipContext.Fluid.NONE && LEVEL_OK.get(level.getClass());
    }

    /**
     * 1.21.1 generic stand-down: the first other mixin merged into BlockGetter's clip path (an @Overwrite of clip, of its
     * step or miss lambda, of traverseBlocks or clipWithInteractionOverride), or any injector handler merged into
     * BlockGetter (its target cannot be told from the handler), as "mixin (method)"; "" when BlockGetter's clip is
     * vanilla's. Mixin marks every method it merges with @MixinMerged (runtime-visible), and BlockGetter is fully
     * transformed when it is loaded, i.e. before this class can read it.
     */
    private static String otherClipMixin() {
        String found = "";
        try {
            for (Method m : BlockGetter.class.getDeclaredMethods()) {
                String mixin = mergedBy(m);
                if (mixin == null) continue;
                String n = m.getName();
                boolean path = n.equals("clip") || n.startsWith("lambda$clip$") || n.equals("traverseBlocks") || n.equals("clipWithInteractionOverride");
                boolean handler = n.indexOf('$') > 0 && !n.startsWith("lambda$");
                if (path || handler) {
                    found = mixin + " (" + n + ")";
                    break;
                }
            }
        } catch (Throwable t) {
            found = "an unreadable BlockGetter (" + t + ")";
        }
        if (!found.isEmpty()) {
            LOGGER.info("Bons and Furious: vanilla_raycast_fluid_none stands down: BlockGetter's clip is changed by {}; every clip runs that method", found);
        }
        return found;
    }

    private static String mergedBy(Method m) {
        for (Annotation a : m.getDeclaredAnnotations()) {
            if (a.annotationType().getName().equals("org.spongepowered.asm.mixin.transformer.meta.MixinMerged")) {
                try {
                    return String.valueOf(a.annotationType().getMethod("mixin").invoke(a));
                } catch (Throwable t) {
                    return "a mixin";
                }
            }
        }
        return null;
    }

    public static BlockHitResult clip(BlockGetter level, ClipContext context) {
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: vanilla_raycast_fluid_none applies (raycasts that ignore fluids skip the fluid reads){}",
                    SHADOW ? " - shadow verification on" : "");
        }
        return BlockGetter.traverseBlocks(context.getFrom(), context.getTo(), context, (c, pos) -> step(level, c, pos), ClipFast::miss);
    }

    /** One step of the ray, as the original step but without the fluid read (Fluid.NONE ignores it). */
    static BlockHitResult step(BlockGetter level, ClipContext c, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        Vec3 from = c.getFrom();
        Vec3 to = c.getTo();
        VoxelShape shape = c.getBlockShape(state, level, pos);
        BlockHitResult hit = level.clipWithInteractionOverride(from, to, pos, shape, state);
        VoxelShape fluidShape = c.getFluidShape(emptyFluid(), level, pos);
        BlockHitResult fluidHit = fluidShape.clip(from, to, pos);
        double d0 = hit == null ? Double.MAX_VALUE : c.getFrom().distanceToSqr(hit.getLocation());
        double d1 = fluidHit == null ? Double.MAX_VALUE : c.getFrom().distanceToSqr(fluidHit.getLocation());
        return d0 <= d1 ? hit : fluidHit;
    }

    static BlockHitResult miss(ClipContext c) {
        Vec3 back = c.getFrom().subtract(c.getTo());
        return BlockHitResult.miss(c.getTo(), Direction.getNearest(back.x, back.y, back.z), BlockPos.containing(c.getTo()));
    }

    private static FluidState emptyFluid() {
        FluidState e = empty;
        if (e == null) empty = e = Fluids.EMPTY.defaultFluidState();
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
        return h == null ? "null" : h.getType() + " " + h.getLocation() + " " + h.getDirection() + " " + h.getBlockPos() + " " + h.isInside();
    }
}
