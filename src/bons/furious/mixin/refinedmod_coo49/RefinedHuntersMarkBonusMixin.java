package bons.furious.mixin.refinedmod_coo49;

import bons.furious.patch.refinedmod_coo49.RefinedFixes;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * refinedmod_hunters_mark_bonus_floor (Refined Mod 1.1.6, All Rights Reserved; both sides; since 1.0.49; change, gameplay
 * fix, off by default): MixinExtras @ModifyReturnValue on HuntersMarkHandler.getBonusModifier: a negative bonus (a level 1
 * mark cast with more than 2 spell power) becomes 0, so the mark never lowers the marked hits' damage. Helper RefinedFixes.
 */
@Mixin(targets = "net.refinedrain.refinedmod.effect.hunters_mark.HuntersMarkHandler", remap = false)
public abstract class RefinedHuntersMarkBonusMixin {
    @ModifyReturnValue(method = "getBonusModifier(ILnet/minecraft/world/entity/LivingEntity;)F", at = @At("RETURN"), require = 1, allow = 1)
    private static float bons$refinedmodCoo49BonusFloor(float answer) {
        return RefinedFixes.bonus(answer);
    }
}
