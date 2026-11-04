package bons.furious.mixin.supplementaries_lean;

import net.minecraftforge.common.capabilities.CapabilityProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * supplementaries_sign_ink_sync_filter (Forge 47.4.28, both sides): read access to CapabilityProvider.valid, false between
 * invalidateCaps and reviveCaps (getCapability then answers empty whatever is attached). SignInkSync leaves a block entity
 * with invalidated capabilities to Supplementaries' unchanged path. Adds no behaviour and carries no Forge code.
 */
@Mixin(value = CapabilityProvider.class, remap = false)
public interface CapabilityProviderValidAccessor {
    @Accessor(value = "valid", remap = false)
    boolean bons$suppCapsValid();
}
