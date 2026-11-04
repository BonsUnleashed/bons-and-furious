package bons.furious.mixin.curios_presence;

import net.minecraftforge.common.capabilities.CapabilityDispatcher;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * curios_inventory_lookup_layer (Forge 47.4.28, since 1.0.49): read access to CapabilityDispatcher.caps (the final provider
 * array the dispatcher walks; ModernFix's generated dispatcher walks the same array), so CuriosInventoryLookup can find the
 * entity's own Curios provider when it learns an answer. Adds no behaviour.
 */
@Mixin(value = CapabilityDispatcher.class, remap = false)
public interface CapabilityDispatcherCuriosLookupAccessor {
    @Accessor(value = "caps", remap = false)
    ICapabilityProvider[] bons$lookupProviders();
}
