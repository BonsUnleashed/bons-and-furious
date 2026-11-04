package bons.furious.mixin.attribute_lookup;

import java.util.Map;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Read access to MobEffect's attribute modifier templates (f_19446_, the map MobEffect.addAttributeModifiers and
 * removeAttributeModifiers iterate) for vanilla_effect_refresh_modifier_keep (Minecraft 1.20.1; server; since 1.0.49).
 */
@Mixin(value = MobEffect.class, remap = false)
public interface MobEffectTemplatesAccessor {
    @Accessor(value = "f_19446_", remap = false)
    Map<Attribute, AttributeModifier> bons$refreshTemplates();
}
