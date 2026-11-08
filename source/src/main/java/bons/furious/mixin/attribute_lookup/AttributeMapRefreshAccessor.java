package bons.furious.mixin.attribute_lookup;

import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Read access to AttributeMap's instance map (attributes) and supplier (supplier) for vanilla_effect_refresh_modifier_keep
 * (Minecraft 1.21.1; server; its own accessor, so the switch needs no other switch): the check looks instances up without
 * AttributeMap.getInstance's computeIfAbsent, so it creates nothing.
 */
@Mixin(value = AttributeMap.class, remap = false)
public interface AttributeMapRefreshAccessor {
    @Accessor(value = "attributes", remap = false)
    Map<Holder<Attribute>, AttributeInstance> bons$refreshInstances();

    @Accessor(value = "supplier", remap = false)
    AttributeSupplier bons$refreshSupplier();
}
