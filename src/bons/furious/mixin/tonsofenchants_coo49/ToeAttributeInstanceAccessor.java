package bons.furious.mixin.tonsofenchants_coo49;

import java.util.Set;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * tonsofenchants_absent_modifier_removal_skip (both sides): AttributeInstance's permanent-modifier set (f_22091_, read
 * only) for the no-op test, and its recompute mark (f_22093_), which the left-out removal still sets as the original's
 * setDirty does (without setDirty's change notice). Adds no behaviour.
 */
@Mixin(value = AttributeInstance.class, remap = false)
public interface ToeAttributeInstanceAccessor {
    @Accessor(value = "f_22091_", remap = false)
    Set<AttributeModifier> bons$tonsofenchantsCoo49Permanent();

    @Accessor(value = "f_22093_", remap = false)
    void bons$tonsofenchantsCoo49Dirty(boolean dirty);
}
