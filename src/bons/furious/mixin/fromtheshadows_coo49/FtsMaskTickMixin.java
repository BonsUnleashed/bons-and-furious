package bons.furious.mixin.fromtheshadows_coo49;

import bons.furious.patch.fromtheshadows_coo49.FtsMask;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * fromtheshadows_mask_tick_precheck (From The Shadows Reborn 2.9, All Rights Reserved; both sides): MixinExtras
 * @ModifyExpressionValue on the first event.getEntity() call of ServerEvents.onLivingUpdateEvent, whose value only feeds
 * the handler's opening "instanceof LivingEntity" test: when FtsMask says the handler cannot act for this entity (a client
 * level, or no plague doctor mask in any armour slot), the value is null, the test fails and the handler returns at once,
 * which is where the original ends after reads only. Otherwise the original value: the handler runs as shipped. Our own
 * logic only; nothing of the mod is carried.
 */
@Mixin(targets = "net.sonmok14.fromtheshadows.server.utils.event.ServerEvents", remap = false)
public abstract class FtsMaskTickMixin {
    @ModifyExpressionValue(method = "onLivingUpdateEvent(Lnet/minecraftforge/event/entity/living/LivingEvent$LivingTickEvent;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraftforge/event/entity/living/LivingEvent$LivingTickEvent;getEntity()Lnet/minecraft/world/entity/LivingEntity;", ordinal = 0),
            require = 1, allow = 1)
    private LivingEntity bons$fromtheshadowsCoo49MaskPrecheck(LivingEntity original) {
        return FtsMask.cannotAct(original) ? null : original;
    }
}
