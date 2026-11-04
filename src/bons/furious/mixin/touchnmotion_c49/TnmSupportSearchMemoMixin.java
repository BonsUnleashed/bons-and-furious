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
import strm.touchnmotion.transport.SupportSearch;

/**
 * touchnmotion_pose_block_memo (Touch'n Motion 1.0.4, client, since 1.0.49): SupportSearch's four probes (deck, find,
 * clear, valid: standing and bracing on vehicles and decks). @Redirect of each `new ClipContext(..)` and
 * the Level.clip right after it: COLLIDER + Fluid.NONE probes walk directly; find's and valid's
 * OUTLINE probes run as shipped (TnmProbe). No Touch'n Motion code is carried (GPL-3.0).
 */
@Mixin(value = SupportSearch.class, remap = false)
public abstract class TnmSupportSearchMemoMixin {
    @Redirect(method = {"deck", "find", "clear", "valid"}, require = 4, allow = 4, at = @At(value = "NEW",
            target = "(Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/level/ClipContext$Block;Lnet/minecraft/world/level/ClipContext$Fluid;Lnet/minecraft/world/entity/Entity;)Lnet/minecraft/world/level/ClipContext;"))
    private static ClipContext bons$touchnmotionC49SupportContext(Vec3 from, Vec3 to, ClipContext.Block block, ClipContext.Fluid fluid, Entity entity) {
        return TnmFrame.open ? TnmProbe.context(from, to, block, fluid, entity, TnmProbe.MEMO_SITE) : new ClipContext(from, to, block, fluid, entity);
    }

    @Redirect(method = {"deck", "find", "clear", "valid"}, require = 4, allow = 4, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;m_45547_(Lnet/minecraft/world/level/ClipContext;)Lnet/minecraft/world/phys/BlockHitResult;"))
    private static BlockHitResult bons$touchnmotionC49SupportClip(Level level, ClipContext context) {
        return context == TnmProbe.INERT ? TnmProbe.clip(level) : level.m_45547_(context);
    }
}
