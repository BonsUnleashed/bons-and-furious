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
import strm.touchnmotion.torso.Stride;

/**
 * touchnmotion_ground_probe_direct (Touch'n Motion 1.0.4, client, since 1.0.49): Stride.level's ground probe (3 x 9 per
 * leg while a stance levels a step). @Redirect of its `new ClipContext(.., COLLIDER, NONE, player)` and
 * on the Level.clip right after it: the probe walks directly (TnmProbe). No Touch'n Motion code is carried (GPL-3.0).
 */
@Mixin(value = Stride.class, remap = false)
public abstract class TnmStrideProbeMixin {
    @Redirect(method = "level(Lnet/minecraft/client/player/AbstractClientPlayer;Lstrm/emfcompat/core/ik/IKFrame;Lnet/minecraft/client/model/geom/ModelPart;Lorg/joml/Vector3f;Lorg/joml/Vector3f;DDD)Z",
            require = 1, allow = 1, at = @At(value = "NEW",
            target = "(Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/level/ClipContext$Block;Lnet/minecraft/world/level/ClipContext$Fluid;Lnet/minecraft/world/entity/Entity;)Lnet/minecraft/world/level/ClipContext;"))
    private static ClipContext bons$touchnmotionC49StrideContext(Vec3 from, Vec3 to, ClipContext.Block block, ClipContext.Fluid fluid, Entity entity) {
        return TnmFrame.open ? TnmProbe.context(from, to, block, fluid, entity, TnmProbe.STRIDE) : new ClipContext(from, to, block, fluid, entity);
    }

    @Redirect(method = "level(Lnet/minecraft/client/player/AbstractClientPlayer;Lstrm/emfcompat/core/ik/IKFrame;Lnet/minecraft/client/model/geom/ModelPart;Lorg/joml/Vector3f;Lorg/joml/Vector3f;DDD)Z",
            require = 1, allow = 1, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;m_45547_(Lnet/minecraft/world/level/ClipContext;)Lnet/minecraft/world/phys/BlockHitResult;"))
    private static BlockHitResult bons$touchnmotionC49StrideClip(Level level, ClipContext context) {
        return context == TnmProbe.INERT ? TnmProbe.clip(level) : level.m_45547_(context);
    }
}
