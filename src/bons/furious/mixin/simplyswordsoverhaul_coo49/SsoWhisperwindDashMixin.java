package bons.furious.mixin.simplyswordsoverhaul_coo49;

import bons.furious.patch.simplyswordsoverhaul_coo49.SsoDash;
import com.bawnorton.mixinsquared.TargetHandler;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.entity.player.Player;
import net.sweenus.simplyswords.item.custom.WhisperwindSwordItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * simplyswordsoverhaul_logout_dash_restore, Whisperwind part (Simply Swords Overhaul 1.0.4 with Simply Swords 1.56.0; MIT:
 * no code of the mod is carried; server side; since 1.0.49). Gameplay fix, off by default. MixinSquared injections into
 * Simply Swords Overhaul's MixinWhisperwindSword.modifyUseMethod handler (merged into WhisperwindSwordItem.use at its
 * head): MixinExtras @WrapOperation on its Scheduler.schedule calls hands Simply Swords Overhaul's own dash steps and dash
 * end to SsoDash.task (the same task, run by the same scheduler at the same tick, with the bookkeeping around it), and on
 * its Player.setNoGravity calls tells SsoDash.gravity what the handler itself did to the player's gravity. Priority 1500:
 * applied after Simply Swords Overhaul's mixin. require = 0 and expect = 0: without Simply Swords Overhaul's handler in the
 * class the injections find nothing; with it the switch's guard lines pin the handler, and the group's offline proof
 * applies Simply Swords Overhaul's own mixin and checks the merged handler calls these wrappers.
 */
@Mixin(value = WhisperwindSwordItem.class, priority = 1500, remap = false)
public abstract class SsoWhisperwindDashMixin {
    @TargetHandler(mixin = "net.jrdemiurge.simplyswordsoverhaul.mixin.MixinWhisperwindSword", name = "modifyUseMethod")
    @WrapOperation(method = "@MixinSquared:Handler",
            at = @At(value = "INVOKE", target = "Lnet/jrdemiurge/simplyswordsoverhaul/scheduler/Scheduler;schedule(Ljava/lang/Runnable;II)V"),
            require = 0, expect = 0, allow = 2)
    private void bons$simplyswordsoverhaulCoo49WhisperwindTask(Runnable task, int delay, int period, Operation<Void> original,
            @Local(argsOnly = true) Player player) {
        original.call(SsoDash.task(player, task, period, SsoDash.WHISPERWIND), delay, period);
    }

    @TargetHandler(mixin = "net.jrdemiurge.simplyswordsoverhaul.mixin.MixinWhisperwindSword", name = "modifyUseMethod")
    @WrapOperation(method = "@MixinSquared:Handler",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;m_20242_(Z)V"),
            require = 0, expect = 0, allow = 2)
    private void bons$simplyswordsoverhaulCoo49WhisperwindGravity(Player player, boolean noGravity, Operation<Void> original) {
        boolean before = player.m_20068_();
        original.call(player, noGravity);
        SsoDash.gravity(player, before);
    }
}
