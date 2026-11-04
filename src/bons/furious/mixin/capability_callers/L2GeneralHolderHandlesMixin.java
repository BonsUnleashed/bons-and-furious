package bons.furious.mixin.capability_callers;

import bons.furious.patch.capability_callers.L2LibraryHandles;
import dev.xkmc.l2library.capability.entity.GeneralCapabilityHolder;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * l2library_capability_handles (L2 Library 2.5.5, LGPL-2.1; both sides; since 1.0.49): the provider.getCapability(capability)
 * calls of GeneralCapabilityHolder.get and isProper answered from the layer's kept handle when the provider is an entity
 * (L2LibraryHandles); any other provider is asked as shipped.
 */
@Mixin(value = GeneralCapabilityHolder.class, remap = false)
public abstract class L2GeneralHolderHandlesMixin {
    @Redirect(method = {"get(Lnet/minecraftforge/common/capabilities/ICapabilityProvider;)Ldev/xkmc/l2library/capability/entity/GeneralCapabilityTemplate;",
            "isProper(Lnet/minecraftforge/common/capabilities/ICapabilityProvider;)Z"},
            at = @At(value = "INVOKE", target = "Lnet/minecraftforge/common/capabilities/ICapabilityProvider;getCapability(Lnet/minecraftforge/common/capabilities/Capability;)Lnet/minecraftforge/common/util/LazyOptional;"))
    private <T> LazyOptional<T> bons$l2Handle(ICapabilityProvider provider, Capability<T> capability) {
        return L2LibraryHandles.lookup(provider, capability);
    }
}
