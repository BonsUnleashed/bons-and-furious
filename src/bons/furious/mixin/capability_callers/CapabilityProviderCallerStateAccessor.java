package bons.furious.mixin.capability_callers;

import net.minecraftforge.common.capabilities.CapabilityDispatcher;
import net.minecraftforge.common.capabilities.CapabilityProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * forge_capability_handle_layer (Forge 47.4.16, since 1.0.46): read access to CapabilityProvider.valid (cleared by
 * invalidateCaps, set again by reviveCaps) and CapabilityProvider.capabilities (the dispatcher built once by
 * gatherCapabilities), which CallerCapabilityHandles checks before it hands out a kept capability answer. Adds no
 * behaviour. Separate from the Mowzie's and Cataclysm accessors so each switch can be off on its own.
 */
@Mixin(value = CapabilityProvider.class, remap = false)
public interface CapabilityProviderCallerStateAccessor {
    @Accessor(value = "valid", remap = false)
    boolean bons$callerCapsValid();

    @Accessor(value = "capabilities", remap = false)
    CapabilityDispatcher bons$callerCapDispatcher();
}
