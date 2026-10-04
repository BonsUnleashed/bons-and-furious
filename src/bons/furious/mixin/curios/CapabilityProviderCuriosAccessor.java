package bons.furious.mixin.curios;

import net.minecraftforge.common.capabilities.CapabilityDispatcher;
import net.minecraftforge.common.capabilities.CapabilityProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * curios_slotless_tick_skip (Forge 47.4.16): read access to CapabilityProvider.valid (cleared by invalidateCaps, set again
 * by reviveCaps) and CapabilityProvider.capabilities (the dispatcher gatherCapabilities builds once), which
 * CuriosSlotlessTick checks before it remembers or uses an empty inventory answer. Adds no behaviour.
 */
@Mixin(value = CapabilityProvider.class, remap = false)
public interface CapabilityProviderCuriosAccessor {
    @Accessor(value = "valid", remap = false)
    boolean bons$curiosCapsValid();

    @Accessor(value = "capabilities", remap = false)
    CapabilityDispatcher bons$curiosDispatcher();
}
