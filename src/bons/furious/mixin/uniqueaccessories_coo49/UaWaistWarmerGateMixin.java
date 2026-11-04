package bons.furious.mixin.uniqueaccessories_coo49;

import bons.furious.patch.uniqueaccessories_coo49.UaWaistWarmer;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * uniqueaccessories_waist_warmer_frozen_first (Unique Accessories 1.3.3, All Rights Reserved; server side): MixinExtras
 * @WrapOperation on the hasWaistWarmer call of WaistWarmerEvents.onLivingTick (see UaWaistWarmer: the walk is left out
 * where its answer cannot matter). Our own logic only; nothing of the mod is carried.
 */
@Mixin(targets = "net.genzyuro.uniqueaccessories.system.WaistWarmerEvents", remap = false)
public abstract class UaWaistWarmerGateMixin {
    @WrapOperation(method = "onLivingTick(Lnet/minecraftforge/event/entity/living/LivingEvent$LivingTickEvent;)V",
            at = @At(value = "INVOKE", target = "Lnet/genzyuro/uniqueaccessories/system/WaistWarmerEvents;hasWaistWarmer(Lnet/minecraft/world/entity/LivingEntity;)Z"),
            require = 1, allow = 1, remap = false)
    private static boolean bons$uniqueaccessoriesCoo49WaistWarmer(LivingEntity entity, Operation<Boolean> original) {
        return UaWaistWarmer.hasWaistWarmer(entity, original);
    }
}
