package bons.furious.mixin.cataclysm;

import net.minecraftforge.common.capabilities.CapabilityDispatcher;
import net.minecraftforge.common.capabilities.CapabilityProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * cataclysm_capability_handles (Forge 47.4.16, since 1.0.36): read access to CapabilityProvider.valid (cleared by
 * invalidateCaps, set again by reviveCaps) and CapabilityProvider.capabilities (the dispatcher built once by
 * gatherCapabilities), which PrivateCapabilityHandles checks before it hands out a kept capability answer. Adds no
 * behaviour. Separate from mowziesmobs_capability_handles' accessor so either switch can be off on its own.
 */
@Mixin(value = CapabilityProvider.class, remap = false)
public interface CapabilityProviderTokenStateAccessor {
    @Accessor(value = "valid", remap = false)
    boolean bons$tokenCapsValid();

    @Accessor(value = "capabilities", remap = false)
    CapabilityDispatcher bons$tokenCapDispatcher();
}
