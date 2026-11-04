package bons.furious.mixin.attribute_lookup;

import java.util.Map;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Read access to AttributeMap's instance map (f_22139_) and supplier (f_22141_) for vanilla_effect_refresh_modifier_keep
 * (Minecraft 1.20.1; server; since 1.0.49; its own accessor, so the switch needs no other switch): the check looks
 * instances up without AttributeMap.getInstance's computeIfAbsent, so it creates nothing.
 */
@Mixin(value = AttributeMap.class, remap = false)
public interface AttributeMapRefreshAccessor {
    @Accessor(value = "f_22139_", remap = false)
    Map<Attribute, AttributeInstance> bons$refreshInstances();

    @Accessor(value = "f_22141_", remap = false)
    AttributeSupplier bons$refreshSupplier();
}
