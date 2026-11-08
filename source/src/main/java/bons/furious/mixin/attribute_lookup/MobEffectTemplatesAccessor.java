package bons.furious.mixin.attribute_lookup;

import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.ai.attributes.Attribute;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Read access to MobEffect's attribute modifier templates (attributeModifiers: attribute holder -> AttributeTemplate, the
 * map MobEffect.addAttributeModifiers and removeAttributeModifiers iterate) for vanilla_effect_refresh_modifier_keep
 * (Minecraft 1.21.1; server).
 */
@Mixin(value = MobEffect.class, remap = false)
public interface MobEffectTemplatesAccessor {
    @Accessor(value = "attributeModifiers", remap = false)
    Map<Holder<Attribute>, MobEffect.AttributeTemplate> bons$refreshTemplates();
}
