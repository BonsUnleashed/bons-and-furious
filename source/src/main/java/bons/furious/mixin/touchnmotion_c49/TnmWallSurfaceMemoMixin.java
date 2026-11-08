package bons.furious.mixin.touchnmotion_c49;

import bons.furious.patch.touchnmotion_c49.TnmFrame;
import bons.furious.patch.touchnmotion_c49.TnmProbe;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import strm.touchnmotion.wallhand.WallSurface;

/**
 * touchnmotion_pose_block_memo (Touch'n Motion 1.0.4 for NeoForge 1.21.1, client, since 1.0.49): WallSurface.clip's wall
 * probe (hand on wall poses). @Redirect of its `new ClipContext(.., COLLIDER, NONE, player)` and the Level.clip right
 * after it: the probe walks directly (TnmProbe). No Touch'n Motion code is carried (GPL-3.0).
 */
@Mixin(value = WallSurface.class, remap = false)
public abstract class TnmWallSurfaceMemoMixin {
    @Redirect(method = "clip", require = 1, allow = 1, at = @At(value = "NEW",
            target = "(Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/level/ClipContext$Block;Lnet/minecraft/world/level/ClipContext$Fluid;Lnet/minecraft/world/entity/Entity;)Lnet/minecraft/world/level/ClipContext;"))
    private static ClipContext bons$touchnmotionC49WallContext(Vec3 from, Vec3 to, ClipContext.Block block, ClipContext.Fluid fluid, Entity entity) {
        return TnmFrame.open ? TnmProbe.context(from, to, block, fluid, entity, TnmProbe.MEMO_SITE) : new ClipContext(from, to, block, fluid, entity);
    }

    @Redirect(method = "clip", require = 1, allow = 1, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;clip(Lnet/minecraft/world/level/ClipContext;)Lnet/minecraft/world/phys/BlockHitResult;"))
    private static BlockHitResult bons$touchnmotionC49WallClip(Level level, ClipContext context) {
        return context == TnmProbe.INERT ? TnmProbe.clip(level) : level.clip(context);
    }
}
