package bons.furious.mixin.mowziesmobs;

import net.minecraftforge.common.capabilities.CapabilityDispatcher;
import net.minecraftforge.common.capabilities.CapabilityProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * mowziesmobs_capability_handles (Forge 47.4.16): read access to CapabilityProvider.valid (cleared by invalidateCaps, set
 * again by reviveCaps) and CapabilityProvider.capabilities (the dispatcher built once by gatherCapabilities), which
 * MowzieCapabilityHandles checks before it hands out a kept capability answer. Adds no behaviour.
 */
@Mixin(value = CapabilityProvider.class, remap = false)
public interface CapabilityProviderStateAccessor {
    @Accessor(value = "valid", remap = false)
    boolean bons$mowzieCapsValid();

    @Accessor(value = "capabilities", remap = false)
    CapabilityDispatcher bons$mowzieCapDispatcher();
}
