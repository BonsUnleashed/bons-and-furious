package bons.furious.mixin.attribute_lookup;

import bons.furious.patch.attribute_lookup.AttributeLookup;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.Map;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * vanilla_attribute_instance_fast_lookup (Minecraft 1.20.1, both sides, since 1.0.49; SRG names).
 *
 * AttributeMap.getInstance (m_22146_) is wrapped as a whole (MixinExtras @WrapMethod, no CallbackInfo): an attribute the
 * map already holds is returned from the map, one the entity type's supplier does not have answers null, everything else
 * runs the original computeIfAbsent (see AttributeLookup for why every answer and the map's contents are unchanged). No
 * Minecraft code is carried; works on vanilla's HashMap and on the fastutil map Radium puts in the same field.
 */
@Mixin(value = AttributeMap.class, remap = false)
public abstract class AttributeMapFastLookupMixin {
    @Shadow
    @Final
    private Map<Attribute, AttributeInstance> f_22139_;

    @Shadow
    @Final
    private AttributeSupplier f_22141_;

    @WrapMethod(method = "m_22146_")
    private AttributeInstance bons$fastInstance(Attribute attribute, Operation<AttributeInstance> original) {
        if (AttributeLookup.enabled) {
            AttributeInstance instance = this.f_22139_.get(attribute);
            if (instance != null) return AttributeLookup.SHADOW ? AttributeLookup.shadow(attribute, instance, original.call(attribute)) : instance;
            if (!this.f_22141_.m_22258_(attribute))
                return AttributeLookup.SHADOW ? AttributeLookup.shadow(attribute, null, original.call(attribute)) : null;
        }
        return original.call(attribute);
    }
}
