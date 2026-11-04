package bons.furious.mixin.traveloptics_coo49;

import bons.furious.patch.traveloptics_coo49.ToClimbCurio;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.common.util.LazyOptional;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * traveloptics_climb_curio_effect_first (T.O Magic 'n Extras 6.3.0, All Rights Reserved; both sides; exact): MixinExtras
 * @WrapOperation on the CuriosApi.getCuriosInventory call of ForgeServerEvents.applyClimbOnCurio (the living tick handler
 * that lets a wearer of the Sigil of the Spider Sorcerer with Spider Aspect climb walls). When the entity has an inventory,
 * does not have Spider Aspect (so the handler's "sigil found AND Spider Aspect" test is false whatever the walk finds) and the
 * shared Curios layer says a walk over that inventory now would only read, the empty optional stands in, so the handler's
 * findCurios walk over every slot is not made. Otherwise the original answer. See ToClimbCurio. Our own logic only.
 */
@Mixin(targets = "com.gametechbc.traveloptics.events.ForgeServerEvents", remap = false)
public abstract class ToClimbCurioPrecheckMixin {
    @SuppressWarnings({"rawtypes"})
    @WrapOperation(method = "applyClimbOnCurio(Lnet/minecraftforge/event/entity/living/LivingEvent$LivingTickEvent;)V",
            at = @At(value = "INVOKE", target = "Ltop/theillusivec4/curios/api/CuriosApi;getCuriosInventory(Lnet/minecraft/world/entity/LivingEntity;)Lnet/minecraftforge/common/util/LazyOptional;"),
            require = 1, allow = 1, remap = false)
    private static LazyOptional bons$travelopticsCoo49ClimbInventory(LivingEntity entity, Operation<LazyOptional> original) {
        return ToClimbCurio.inventory(entity, original);
    }
}
