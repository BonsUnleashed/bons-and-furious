package bons.furious.mixin.fallentraits_coo49;

import bons.furious.patch.fallentraits_coo49.FtInfernal;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * fallentraits_infernal_follow_range_cap (Fallen Traits & Relics 1.0.0; server side; trade-off, off by default): MixinExtras
 * @ModifyExpressionValue on the getFollowDistance() (m_7623_) call of NearestAttackableTargetGoal.findTarget (m_26073_),
 * whose value only sizes the search box for non-player targets: for a mob whose FOLLOW_RANGE carries Fallen Traits'
 * Infernal modifier a distance above 256 becomes 256 (FtInfernal). Keeping a target (canContinueToUse) and player
 * targeting through the goal's TargetingConditions keep the full range.
 */
@Mixin(value = NearestAttackableTargetGoal.class, remap = false)
public abstract class FtInfernalTargetMixin extends TargetGoal {
    private FtInfernalTargetMixin(Mob mob, boolean mustSee) {
        super(mob, mustSee);
    }

    @ModifyExpressionValue(method = "m_26073_()V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/ai/goal/target/NearestAttackableTargetGoal;m_7623_()D"),
            require = 1, allow = 1)
    private double bons$fallentraitsCoo49InfernalTargetRange(double distance) {
        return FtInfernal.clamp(this.f_26135_, distance);
    }
}
