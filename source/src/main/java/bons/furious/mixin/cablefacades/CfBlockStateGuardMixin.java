package bons.furious.mixin.cablefacades;

import bons.furious.patch.cablefacades.CfFastExit;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * cablefacades_no_facade_fast_exit (Cable Facades 2.1.3, both sides): redirects of the recursion-guard read (the first
 * ThreadLocal.get()) of the five methods Cable Facades' BlockStateMixin adds to BlockState (getAppearance, getLightEmission,
 * isFaceSturdy isFaceSturdy, getVisualShape getVisualShape, getLightBlock getLightBlock), capturing the method's getter and position.
 * Answered "held" when no facade can exist (see CfFastExit), the method takes its guard-held branch: the same super /
 * getBlock().getX(asState(), ...) call its null-lookup path makes. Our own logic only. Priority 1500: these methods are
 * merged by Cable Facades' mixin at priority 1000, and Mixin lets a higher-priority injector into them. require = 0:
 * without that mixin there is nothing to redirect.
 */
@Mixin(value = BlockState.class, priority = 1500, remap = false)
public abstract class CfBlockStateGuardMixin {
    @Redirect(method = "getAppearance(Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;",
            require = 0, expect = 0, at = @At(value = "INVOKE", target = "Ljava/lang/ThreadLocal;get()Ljava/lang/Object;", ordinal = 0))
    private Object bons$cfAppearance(ThreadLocal<?> guard, BlockAndTintGetter getter, BlockPos pos) {
        return CfFastExit.guardClient(guard, pos);
    }

    @Redirect(method = "getLightEmission(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)I",
            require = 0, expect = 0, at = @At(value = "INVOKE", target = "Ljava/lang/ThreadLocal;get()Ljava/lang/Object;", ordinal = 0))
    private Object bons$cfLightEmission(ThreadLocal<?> guard, BlockGetter getter, BlockPos pos) {
        return CfFastExit.guard(guard, getter, pos);
    }

    @Redirect(method = "isFaceSturdy(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;Lnet/minecraft/world/level/block/SupportType;)Z",
            require = 0, expect = 0, at = @At(value = "INVOKE", target = "Ljava/lang/ThreadLocal;get()Ljava/lang/Object;", ordinal = 0))
    private Object bons$cfFaceSturdy(ThreadLocal<?> guard, BlockGetter getter, BlockPos pos) {
        return CfFastExit.guard(guard, getter, pos);
    }

    @Redirect(method = "getVisualShape(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/phys/shapes/CollisionContext;)Lnet/minecraft/world/phys/shapes/VoxelShape;",
            require = 0, expect = 0, at = @At(value = "INVOKE", target = "Ljava/lang/ThreadLocal;get()Ljava/lang/Object;", ordinal = 0))
    private Object bons$cfVisualShape(ThreadLocal<?> guard, BlockGetter getter, BlockPos pos) {
        return CfFastExit.guard(guard, getter, pos);
    }

    @Redirect(method = "getLightBlock(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)I",
            require = 0, expect = 0, at = @At(value = "INVOKE", target = "Ljava/lang/ThreadLocal;get()Ljava/lang/Object;", ordinal = 0))
    private Object bons$cfLightBlock(ThreadLocal<?> guard, BlockGetter getter, BlockPos pos) {
        return CfFastExit.guard(guard, getter, pos);
    }
}
