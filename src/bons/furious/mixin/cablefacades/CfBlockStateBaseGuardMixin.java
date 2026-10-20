package bons.furious.mixin.cablefacades;

import bons.furious.patch.cablefacades.CfFastExit;
import com.bawnorton.mixinsquared.TargetHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * cablefacades_no_facade_fast_exit (Cable Facades 2.1.3, both sides): MixinSquared @TargetHandler redirects of the
 * recursion-guard read (the first ThreadLocal.get()) of Cable Facades' seven BlockStateBaseMixin handlers merged into
 * BlockStateBase. Each redirect captures the handler's getter and position (Mixin's target-argument capture: plain
 * arguments, nothing allocated). CfFastExit answers "held" when no facade can exist for the getter, so the handler returns
 * at once without touching its CallbackInfo, which is exactly what it does when its lookup finds nothing; otherwise the real
 * guard is read. Our own logic only (Cable Facades' mods.toml says All Rights Reserved). Priority 1500: applied after Cable
 * Facades' mixin (1000). require = 0: if another mod removed that mixin there is nothing to redirect and the class stays as
 * the other mods left it.
 */
@Mixin(value = BlockBehaviour.BlockStateBase.class, priority = 1500, remap = false)
public abstract class CfBlockStateBaseGuardMixin {
    @TargetHandler(mixin = "com.portingdeadmods.cable_facades.mixins.BlockStateBaseMixin", name = "getShape")
    @Redirect(method = "@MixinSquared:Handler", require = 0, expect = 0,
            at = @At(value = "INVOKE", target = "Ljava/lang/ThreadLocal;get()Ljava/lang/Object;", ordinal = 0))
    private Object bons$cfShape(ThreadLocal<?> guard, BlockGetter getter, BlockPos pos) {
        return CfFastExit.guard(guard, getter, pos);
    }

    @TargetHandler(mixin = "com.portingdeadmods.cable_facades.mixins.BlockStateBaseMixin", name = "getCollisionShape")
    @Redirect(method = "@MixinSquared:Handler", require = 0, expect = 0,
            at = @At(value = "INVOKE", target = "Ljava/lang/ThreadLocal;get()Ljava/lang/Object;", ordinal = 0))
    private Object bons$cfCollision(ThreadLocal<?> guard, BlockGetter getter, BlockPos pos) {
        return CfFastExit.guard(guard, getter, pos);
    }

    @TargetHandler(mixin = "com.portingdeadmods.cable_facades.mixins.BlockStateBaseMixin", name = "getOcclusionShape")
    @Redirect(method = "@MixinSquared:Handler", require = 0, expect = 0,
            at = @At(value = "INVOKE", target = "Ljava/lang/ThreadLocal;get()Ljava/lang/Object;", ordinal = 0))
    private Object bons$cfOcclusion(ThreadLocal<?> guard, BlockGetter getter, BlockPos pos) {
        return CfFastExit.guard(guard, getter, pos);
    }

    @TargetHandler(mixin = "com.portingdeadmods.cable_facades.mixins.BlockStateBaseMixin", name = "getLightBlock")
    @Redirect(method = "@MixinSquared:Handler", require = 0, expect = 0,
            at = @At(value = "INVOKE", target = "Ljava/lang/ThreadLocal;get()Ljava/lang/Object;", ordinal = 0))
    private Object bons$cfLightBlock(ThreadLocal<?> guard, BlockGetter getter, BlockPos pos) {
        return CfFastExit.guard(guard, getter, pos);
    }

    @TargetHandler(mixin = "com.portingdeadmods.cable_facades.mixins.BlockStateBaseMixin", name = "propagatesSkylightDown")
    @Redirect(method = "@MixinSquared:Handler", require = 0, expect = 0,
            at = @At(value = "INVOKE", target = "Ljava/lang/ThreadLocal;get()Ljava/lang/Object;", ordinal = 0))
    private Object bons$cfSkylight(ThreadLocal<?> guard, BlockGetter getter, BlockPos pos) {
        return CfFastExit.guard(guard, getter, pos);
    }

    @TargetHandler(mixin = "com.portingdeadmods.cable_facades.mixins.BlockStateBaseMixin", name = "isSolidRender")
    @Redirect(method = "@MixinSquared:Handler", require = 0, expect = 0,
            at = @At(value = "INVOKE", target = "Ljava/lang/ThreadLocal;get()Ljava/lang/Object;", ordinal = 0))
    private Object bons$cfSolidRender(ThreadLocal<?> guard, BlockGetter getter, BlockPos pos) {
        return CfFastExit.guard(guard, getter, pos);
    }

    @TargetHandler(mixin = "com.portingdeadmods.cable_facades.mixins.BlockStateBaseMixin", name = "onRemove")
    @Redirect(method = "@MixinSquared:Handler", require = 0, expect = 0,
            at = @At(value = "INVOKE", target = "Ljava/lang/ThreadLocal;get()Ljava/lang/Object;", ordinal = 0))
    private Object bons$cfRemove(ThreadLocal<?> guard, Level level, BlockPos pos) {
        return CfFastExit.guard(guard, level, pos);
    }
}
