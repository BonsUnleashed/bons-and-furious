package bons.furious.mixin.fallentraits_coo49;

import bons.furious.patch.fallentraits_coo49.FtRelics;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import java.util.UUID;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * fallentraits_relic_modifier_unchanged_skip (Fallen Traits & Relics 1.0.0; server side in practice): MixinExtras
 * @ModifyExpressionValue on wearer.getAttribute(attr) (m_21051_), the first call of the private setOrRemoveModifier of
 * FuryOfInfernal, LawOfScale and TitansHeart (the same method in all three), with the id, name, value and operation
 * arguments: when value != 0 and the instance already holds a transient modifier with the id and the same name, amount
 * and operation, null is answered and the method takes its own early return for a missing instance, so the unchanged
 * modifier is not removed and added again; otherwise the method runs as shipped (FtRelics). Our own logic only.
 */
@Mixin(targets = {"net.kayn.fallen_traits.content.item.curio.FuryOfInfernal", "net.kayn.fallen_traits.content.item.curio.LawOfScale",
        "net.kayn.fallen_traits.content.item.curio.TitansHeart"}, remap = false)
public abstract class FtRelicModifierMixin {
    @ModifyExpressionValue(method = "setOrRemoveModifier(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/ai/attributes/Attribute;Ljava/util/UUID;Ljava/lang/String;DLnet/minecraft/world/entity/ai/attributes/AttributeModifier$Operation;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;m_21051_(Lnet/minecraft/world/entity/ai/attributes/Attribute;)Lnet/minecraft/world/entity/ai/attributes/AttributeInstance;"),
            require = 1, allow = 1)
    private AttributeInstance bons$fallentraitsCoo49RelicModifier(AttributeInstance instance, @Local(argsOnly = true) UUID id, @Local(argsOnly = true) String name,
                                                                @Local(argsOnly = true) double value, @Local(argsOnly = true) AttributeModifier.Operation op) {
        return FtRelics.unchanged(instance, id, name, value, op) ? null : instance;
    }
}
