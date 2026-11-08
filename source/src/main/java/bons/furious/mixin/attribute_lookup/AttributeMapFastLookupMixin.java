package bons.furious.mixin.attribute_lookup;

import bons.furious.patch.attribute_lookup.AttributeLookup;
import bons.furious.patch.attribute_lookup.AttributeReapply;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * vanilla_attribute_instance_fast_lookup (Minecraft 1.21.1, both sides).
 *
 * AttributeMap.getInstance(Holder) is wrapped as a whole (MixinExtras @WrapMethod, no CallbackInfo): for a plain attribute
 * holder (AttributeReapply.plainHolder) an attribute the map already holds is returned from the map, one the entity type's
 * supplier does not have answers null, and everything else, any other holder class included, runs the original
 * computeIfAbsent (see AttributeLookup for why every answer and the map's contents are unchanged). Works on vanilla's
 * Object2ObjectOpenHashMap and on the Object2ReferenceOpenHashMap Radium 0.13.1 puts in the same field. No Minecraft code is
 * carried.
 */
@Mixin(value = AttributeMap.class, remap = false)
public abstract class AttributeMapFastLookupMixin {
    @Shadow
    @Final
    private Map<Holder<Attribute>, AttributeInstance> attributes;

    @Shadow
    @Final
    private AttributeSupplier supplier;

    @WrapMethod(method = "getInstance(Lnet/minecraft/core/Holder;)Lnet/minecraft/world/entity/ai/attributes/AttributeInstance;")
    private AttributeInstance bons$fastInstance(Holder<Attribute> attribute, Operation<AttributeInstance> original) {
        if (AttributeLookup.enabled && AttributeReapply.plainHolder(attribute)) {
            AttributeInstance instance = this.attributes.get(attribute);
            if (instance != null) return AttributeLookup.SHADOW ? AttributeLookup.shadow(attribute, instance, original.call(attribute)) : instance;
            if (!this.supplier.hasAttribute(attribute))
                return AttributeLookup.SHADOW ? AttributeLookup.shadow(attribute, null, original.call(attribute)) : null;
        }
        return original.call(attribute);
    }
}
