package bons.furious.mixin.attribute_lookup;

import java.util.Map;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Read access to AttributeMap's instance map (f_22139_) and supplier (f_22141_) for the attribute re-apply checks
 * (projectiledamage_weapon_modifier_unchanged_skip, vanilla_effect_refresh_modifier_keep; Minecraft 1.20.1; both sides; since
 * 1.0.49): the checks look instances up without AttributeMap.getInstance's computeIfAbsent, so they create nothing.
 */
@Mixin(value = AttributeMap.class, remap = false)
public interface AttributeMapReapplyAccessor {
    @Accessor(value = "f_22139_", remap = false)
    Map<Attribute, AttributeInstance> bons$reapplyInstances();

    @Accessor(value = "f_22141_", remap = false)
    AttributeSupplier bons$reapplySupplier();
}
