package bons.furious.mixin.simplyswordsoverhaul_coo49;

import bons.furious.patch.simplyswordsoverhaul_coo49.SsoDash;
import com.bawnorton.mixinsquared.TargetHandler;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.entity.player.Player;
import net.sweenus.simplyswords.item.custom.StormsEdgeSwordItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * simplyswordsoverhaul_logout_dash_restore, Storm's Edge part (Simply Swords Overhaul 1.0.4 with Simply Swords 1.56.0;
 * MIT: no code of the mod is carried; server side; since 1.0.49). Gameplay fix, off by default. The same two wrappers as
 * SsoWhisperwindDashMixin, in Simply Swords Overhaul's MixinStormsEdgeSword.modifyUseMethod handler (merged into
 * StormsEdgeSwordItem.use at its head): its Scheduler.schedule calls (the dash steps, the landing, the late gravity
 * restore) go through SsoDash.task, its Player.setNoGravity call is reported to SsoDash.gravity. Priority 1500, require = 0
 * and expect = 0 as there.
 */
@Mixin(value = StormsEdgeSwordItem.class, priority = 1500, remap = false)
public abstract class SsoStormsEdgeDashMixin {
    @TargetHandler(mixin = "net.jrdemiurge.simplyswordsoverhaul.mixin.MixinStormsEdgeSword", name = "modifyUseMethod")
    @WrapOperation(method = "@MixinSquared:Handler",
            at = @At(value = "INVOKE", target = "Lnet/jrdemiurge/simplyswordsoverhaul/scheduler/Scheduler;schedule(Ljava/lang/Runnable;II)V"),
            require = 0, expect = 0, allow = 3)
    private void bons$simplyswordsoverhaulCoo49StormsEdgeTask(Runnable task, int delay, int period, Operation<Void> original,
            @Local(argsOnly = true) Player player) {
        original.call(SsoDash.task(player, task, period, SsoDash.STORMS_EDGE), delay, period);
    }

    @TargetHandler(mixin = "net.jrdemiurge.simplyswordsoverhaul.mixin.MixinStormsEdgeSword", name = "modifyUseMethod")
    @WrapOperation(method = "@MixinSquared:Handler",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;m_20242_(Z)V"),
            require = 0, expect = 0, allow = 1)
    private void bons$simplyswordsoverhaulCoo49StormsEdgeGravity(Player player, boolean noGravity, Operation<Void> original) {
        boolean before = player.m_20068_();
        original.call(player, noGravity);
        SsoDash.gravity(player, before);
    }
}
