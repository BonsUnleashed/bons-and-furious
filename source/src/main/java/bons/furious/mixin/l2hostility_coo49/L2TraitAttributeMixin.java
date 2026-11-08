package bons.furious.mixin.l2hostility_coo49;

import bons.furious.patch.l2hostility_coo49.L2TraitReapply;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * l2hostility_trait_attribute_unchanged_skip (L2 Hostility 3.0.18 for Minecraft 1.21.1, LGPL-2.1; server side in practice):
 * MixinExtras @ModifyExpressionValue on the first call of TraitManager.addAttribute, le.getAttribute(attr), with the
 * method's name, factor and operation arguments: when the instance already holds the permanent modifier with the name's id
 * (l2hostility:name) and the same amount and operation, null is answered and the method takes its own early return for a
 * missing instance, so the unchanged modifier is not removed and added again (no dirty mark). Otherwise the instance is
 * answered and the method runs as shipped (L2TraitReapply). Our own logic only.
 */
@Mixin(targets = "dev.xkmc.l2hostility.content.logic.TraitManager", remap = false)
public abstract class L2TraitAttributeMixin {
    @ModifyExpressionValue(method = "addAttribute(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/core/Holder;Ljava/lang/String;DLnet/minecraft/world/entity/ai/attributes/AttributeModifier$Operation;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getAttribute(Lnet/minecraft/core/Holder;)Lnet/minecraft/world/entity/ai/attributes/AttributeInstance;"),
            require = 1, allow = 1)
    private static AttributeInstance bons$l2hostilityCoo49TraitAttribute(AttributeInstance instance, @Local(argsOnly = true) String name,
                                                                       @Local(argsOnly = true) double factor, @Local(argsOnly = true) AttributeModifier.Operation op) {
        return L2TraitReapply.unchanged(instance, name, factor, op) ? null : instance;
    }
}
