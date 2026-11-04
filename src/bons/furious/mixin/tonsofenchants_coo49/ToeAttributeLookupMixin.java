package bons.furious.mixin.tonsofenchants_coo49;

import bons.furious.patch.tonsofenchants_coo49.ToeLean;
import com.deltajay.tonsofenchants.util.PU;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * tonsofenchants_attribute_lookup_once (Tons Of Enchants 1.0, MIT; both sides): cancellable @Inject at the HEAD of
 * PU.AttributeHelper.getPlayerCurrentAttribute(LivingEntity, Attribute), which builds its pair (the attribute instance, its
 * value or 0.0) from three entity.getAttribute calls. With the switch on the pair is built from one call (ToeLean
 * .currentAttribute: the same pair, the same objects; every call of AttributeMap's lookup answers the same instance);
 * otherwise the method runs as shipped. The mod is MIT; the two-line body is rebuilt in our helper, nothing else is carried.
 */
@Mixin(targets = "com.deltajay.tonsofenchants.util.PU$AttributeHelper", remap = false)
public abstract class ToeAttributeLookupMixin {
    @Inject(method = "getPlayerCurrentAttribute(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/ai/attributes/Attribute;)Lcom/deltajay/tonsofenchants/util/PU$OptionalPair;",
            at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    private static void bons$tonsofenchantsCoo49LookupOnce(LivingEntity entity, Attribute attribute,
                                                         CallbackInfoReturnable<PU.OptionalPair<AttributeInstance, Double>> cir) {
        if (ToeLean.lookupOnce && entity != null) cir.setReturnValue(ToeLean.currentAttribute(entity, attribute));
    }
}
