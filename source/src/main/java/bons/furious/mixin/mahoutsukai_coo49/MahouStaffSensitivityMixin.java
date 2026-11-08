package bons.furious.mixin.mahoutsukai_coo49;

import bons.furious.patch.mahoutsukai_coo49.MahouStaffClient;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import stepsword.mahoutsukai.proxy.CommonProxy;

/**
 * mahoutsukai_staff_sensitivity_local_only (Mahou Tsukai 1.21.1-v1.36.31, All Rights Reserved; client dist; gameplay fix,
 * off by default): MixinExtras @WrapWithCondition on the CommonProxy.speedUp call at the end of ServerHandler.tickCheck
 * (a PlayerTickEvent.Post listener), with the handler's event captured: on a client level the call is made only for the
 * local player; calls on server levels run as shipped. See MahouStaffClient. Our own logic only; nothing of the mod is
 * carried.
 */
@Mixin(targets = "stepsword.mahoutsukai.handlers.ServerHandler", remap = false)
public abstract class MahouStaffSensitivityMixin {
    @WrapWithCondition(method = "tickCheck(Lnet/neoforged/neoforge/event/tick/PlayerTickEvent$Post;)V",
            at = @At(value = "INVOKE", target = "Lstepsword/mahoutsukai/proxy/CommonProxy;speedUp()V"), require = 1, allow = 1)
    private boolean bons$mahoutsukaiCoo49StaffSpeedUp(CommonProxy proxy, PlayerTickEvent.Post event) {
        return MahouStaffClient.speedUp(event);
    }
}
