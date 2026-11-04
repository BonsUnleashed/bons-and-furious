package bons.furious.mixin.revelationfix_coo49;

import bons.furious.patch.revelationfix_coo49.RfEvents;
import com.bawnorton.mixinsquared.TargetHandler;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * revelationfix_hurt_by_target_event_skip (RevelationFix 4.8 and 4.7, the Jar-in-Jar of Goety Revelation; both sides, acts
 * where goals run; RevelationFix is all rights reserved: no code of it is carried). MixinSquared @TargetHandler HEAD
 * injections into the three handlers RevelationFix's HurtByTargetGoalMixin merges into HurtByTargetGoal.canUse (m_8036_):
 * canUse$head, canUse$beforeIgnore and canUse$tail each return before they build and post their
 * LivingHurtByTargetGoalEvent.CanUse when RfEvents.skip says no listener would react to it (RfEvents explains why nothing
 * observable changes). Priority 1500: applied after RevelationFix's mixin, which has the default mixin priority 1000 (its
 * config's "priority" 2147483647 orders configs, not mixins; the group's offline apply proves the order). require = 0 and
 * expect = 0: where RevelationFix's handlers are not merged (the mod absent) the injections find nothing and nothing
 * happens; the switch's guard lines pin the three handlers, and the group's proof applies RevelationFix's own mixin and
 * checks every merged handler calls ours.
 */
@Mixin(value = HurtByTargetGoal.class, priority = 1500, remap = false)
public abstract class RfHurtByTargetEventSkipMixin extends TargetGoal {
    RfHurtByTargetEventSkipMixin(Mob mob, boolean mustSee) {
        super(mob, mustSee);
    }

    @TargetHandler(mixin = "com.mega.revelationfix.mixin.HurtByTargetGoalMixin", name = "canUse$head")
    @Inject(method = "@MixinSquared:Handler", at = @At("HEAD"), cancellable = true, require = 0, expect = 0)
    private void bons$revelationfixSkipHeadEvent(CallbackInfoReturnable<Boolean> theirs, CallbackInfo ci) {
        if (RfEvents.skip(RfEvents.HEAD, this.f_26135_)) ci.cancel();
    }

    @TargetHandler(mixin = "com.mega.revelationfix.mixin.HurtByTargetGoalMixin", name = "canUse$beforeIgnore")
    @Inject(method = "@MixinSquared:Handler", at = @At("HEAD"), cancellable = true, require = 0, expect = 0)
    private void bons$revelationfixSkipBeforeIgnoreEvent(CallbackInfoReturnable<Boolean> theirs, CallbackInfo ci) {
        if (RfEvents.skip(RfEvents.BEFORE_IGNORE, this.f_26135_)) ci.cancel();
    }

    @TargetHandler(mixin = "com.mega.revelationfix.mixin.HurtByTargetGoalMixin", name = "canUse$tail")
    @Inject(method = "@MixinSquared:Handler", at = @At("HEAD"), cancellable = true, require = 0, expect = 0)
    private void bons$revelationfixSkipTailEvent(CallbackInfoReturnable<Boolean> theirs, CallbackInfo ci) {
        if (RfEvents.skip(RfEvents.TAIL, this.f_26135_)) ci.cancel();
    }
}
