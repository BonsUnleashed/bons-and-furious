package bons.furious.mixin.puffishattributes_coo49;

import java.util.Map;
import java.util.UUID;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * puffish_attributes_empty_modifier_fast_path (both sides): read access to AttributeInstance's modifier map (f_22090_,
 * modifierById) for the fast path's first test: an instance whose map holds a modifier sends applyTo to the shipped code
 * after one read. Adds no behaviour.
 */
@Mixin(value = AttributeInstance.class, remap = false)
public interface PuffishModifierMapAccessor {
    @Accessor(value = "f_22090_", remap = false)
    Map<UUID, AttributeModifier> bons$puffishattributesCoo49ById();
}
