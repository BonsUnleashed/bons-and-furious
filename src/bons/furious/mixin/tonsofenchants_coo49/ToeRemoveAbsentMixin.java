package bons.furious.mixin.tonsofenchants_coo49;

import bons.furious.patch.tonsofenchants_coo49.ToeLean;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * tonsofenchants_absent_modifier_removal_skip (Tons Of Enchants 1.0, MIT; both sides; fix): MixinExtras @WrapWithCondition
 * on the AttributeInstance.removeModifier(AttributeModifier) call in PU.AttributeHelper.removeModifier's lambda (the only
 * statement of the consumer it hands to the instance's Optional): the call is left out when ToeLean.removeIsNoOp says the
 * removal would change nothing in the instance; then the only things that do not happen are the dirty mark, its value
 * recompute and the attribute sync packet the dirty mark sends. Otherwise the call runs as shipped. Our own logic only.
 */
@Mixin(targets = "com.deltajay.tonsofenchants.util.PU$AttributeHelper", remap = false)
public abstract class ToeRemoveAbsentMixin {
    @WrapWithCondition(method = "lambda$removeModifier$3(Lnet/minecraft/world/entity/ai/attributes/AttributeModifier;Lnet/minecraft/world/entity/ai/attributes/AttributeInstance;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/ai/attributes/AttributeInstance;m_22130_(Lnet/minecraft/world/entity/ai/attributes/AttributeModifier;)V"),
            require = 1, allow = 1)
    private static boolean bons$tonsofenchantsCoo49RemoveAbsent(AttributeInstance instance, AttributeModifier modifier) {
        return !ToeLean.removeIsNoOp(instance, modifier);
    }
}
