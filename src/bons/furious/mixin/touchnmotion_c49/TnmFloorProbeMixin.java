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

/**
 * touchnmotion_ground_probe_direct (Touch'n Motion 1.0.4, client, since 1.0.49): TerrainBalance.floor's probe (one 1.5
 * block vertical segment per sub-level space, 81 to 289 floors per balance solve). @Redirect of its
 * `new ClipContext(.., COLLIDER, NONE, player)` and on the Level.clip right after it: the probe walks directly
 * (TnmProbe); the hit result has the same location, face (interaction-shape override included), block and inside flag,
 * so the floor normal, EjectorLid.onLid and the outline re-clip see the same values. No Touch'n Motion code is carried
 * (GPL-3.0).
 */
@Mixin(targets = "strm.touchnmotion.footgrounding.compat.TerrainBalance", remap = false)   // package-private class
public abstract class TnmFloorProbeMixin {
    @Redirect(method = "floor(Lnet/minecraft/client/player/AbstractClientPlayer;Lnet/minecraft/world/phys/Vec3;Ljava/util/List;)Lstrm/touchnmotion/footgrounding/compat/TerrainBalance$Floor;",
            require = 1, allow = 1, at = @At(value = "NEW",
            target = "(Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/level/ClipContext$Block;Lnet/minecraft/world/level/ClipContext$Fluid;Lnet/minecraft/world/entity/Entity;)Lnet/minecraft/world/level/ClipContext;"))
    private static ClipContext bons$touchnmotionC49FloorContext(Vec3 from, Vec3 to, ClipContext.Block block, ClipContext.Fluid fluid, Entity entity) {
        return TnmFrame.open ? TnmProbe.context(from, to, block, fluid, entity, TnmProbe.FLOOR) : new ClipContext(from, to, block, fluid, entity);
    }

    @Redirect(method = "floor(Lnet/minecraft/client/player/AbstractClientPlayer;Lnet/minecraft/world/phys/Vec3;Ljava/util/List;)Lstrm/touchnmotion/footgrounding/compat/TerrainBalance$Floor;",
            require = 1, allow = 1, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;m_45547_(Lnet/minecraft/world/level/ClipContext;)Lnet/minecraft/world/phys/BlockHitResult;"))
    private static BlockHitResult bons$touchnmotionC49FloorClip(Level level, ClipContext context) {
        return context == TnmProbe.INERT ? TnmProbe.clip(level) : level.m_45547_(context);
    }
}
