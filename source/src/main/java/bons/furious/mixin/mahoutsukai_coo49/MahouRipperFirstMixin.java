package bons.furious.mixin.mahoutsukai_coo49;

import bons.furious.patch.mahoutsukai_coo49.MahouTicks;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * mahoutsukai_ripper_effect_first (Mahou Tsukai 1.21.1-v1.36.31, All Rights Reserved; both sides): @ModifyVariable at the
 * HEAD of TheRipper.ripperLivingTick: for an entity without the Ripper's invisibility effect the parameter becomes null, so
 * the handler's own opening null test ends it where its effect test would have (the two hand-stack reads before that test
 * are pure). See MahouTicks. Our own logic only; nothing of the mod is carried.
 */
@Mixin(targets = "stepsword.mahoutsukai.item.spells.secret.ripper.TheRipper", remap = false)
public abstract class MahouRipperFirstMixin {
    @ModifyVariable(method = "ripperLivingTick(Lnet/minecraft/world/entity/LivingEntity;)V", at = @At("HEAD"), argsOnly = true, require = 1, allow = 1)
    private static LivingEntity bons$mahoutsukaiCoo49RipperFirst(LivingEntity living) {
        return MahouTicks.ripper(living);
    }
}
