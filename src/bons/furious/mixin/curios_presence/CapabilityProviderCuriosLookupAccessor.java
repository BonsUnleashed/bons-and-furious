package bons.furious.mixin.curios_presence;

import net.minecraftforge.common.capabilities.CapabilityDispatcher;
import net.minecraftforge.common.capabilities.CapabilityProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * curios_inventory_lookup_layer (Forge 47.4.28, since 1.0.49): read access to CapabilityProvider.valid (cleared by
 * invalidateCaps, set again by reviveCaps) and CapabilityProvider.capabilities (the dispatcher gatherCapabilities builds
 * once), which CuriosInventoryLookup checks before it hands out or learns a kept answer. Adds no behaviour. Separate from
 * the other groups' accessors so each switch can be off on its own.
 */
@Mixin(value = CapabilityProvider.class, remap = false)
public interface CapabilityProviderCuriosLookupAccessor {
    @Accessor(value = "valid", remap = false)
    boolean bons$lookupCapsValid();

    @Accessor(value = "capabilities", remap = false)
    CapabilityDispatcher bons$lookupDispatcher();
}
