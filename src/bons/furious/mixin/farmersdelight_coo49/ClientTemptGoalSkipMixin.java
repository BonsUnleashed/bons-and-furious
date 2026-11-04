package bons.furious.mixin.farmersdelight_coo49;

import bons.furious.patch.farmersdelight_coo49.ClientTemptSkip;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vectorwing.farmersdelight.common.event.CommonEvents;

/**
 * farmersdelight_client_tempt_goal_skip (Farmer's Delight 1.3.4, both sides; acts on the client): HEAD of
 * CommonEvents.onAnimalsJoinWorld returns for a client level. See ClientTemptSkip.
 */
@Mixin(value = CommonEvents.class, remap = false)
public abstract class ClientTemptGoalSkipMixin {
    @Inject(method = "onAnimalsJoinWorld", at = @At("HEAD"), cancellable = true)
    private static void bons$skipClientCopies(EntityJoinLevelEvent event, CallbackInfo ci) {
        if (ClientTemptSkip.skip(event.getLevel().f_46443_)) ci.cancel();
    }
}
