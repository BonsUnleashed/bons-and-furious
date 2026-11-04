package bons.furious.mixin.simplyswordsoverhaul_coo49;

import bons.furious.patch.simplyswordsoverhaul_coo49.SsoDash;
import com.bawnorton.mixinsquared.TargetHandler;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.entity.player.Player;
import net.sweenus.simplyswords.item.custom.EmberlashSwordItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * simplyswordsoverhaul_logout_dash_restore, Emberlash part (Simply Swords Overhaul 1.0.4 with Simply Swords 1.56.0; MIT:
 * no code of the mod is carried; server side; since 1.0.49). Gameplay fix, off by default. Simply Swords Overhaul's
 * MixinEmberlashSword.modifyUseMethod handler (merged into EmberlashSwordItem.use at its head) turns gravity off and on
 * only inside its scheduled steps, so only its Scheduler.schedule calls are wrapped: they go through SsoDash.task.
 * Priority 1500, require = 0 and expect = 0 as in SsoWhisperwindDashMixin.
 */
@Mixin(value = EmberlashSwordItem.class, priority = 1500, remap = false)
public abstract class SsoEmberlashDashMixin {
    @TargetHandler(mixin = "net.jrdemiurge.simplyswordsoverhaul.mixin.MixinEmberlashSword", name = "modifyUseMethod")
    @WrapOperation(method = "@MixinSquared:Handler",
            at = @At(value = "INVOKE", target = "Lnet/jrdemiurge/simplyswordsoverhaul/scheduler/Scheduler;schedule(Ljava/lang/Runnable;II)V"),
            require = 0, expect = 0, allow = 4)
    private void bons$simplyswordsoverhaulCoo49EmberlashTask(Runnable task, int delay, int period, Operation<Void> original,
            @Local(argsOnly = true) Player player) {
        original.call(SsoDash.task(player, task, period, SsoDash.EMBERLASH), delay, period);
    }
}
