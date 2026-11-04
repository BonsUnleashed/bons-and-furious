package bons.furious.mixin.arseng_coo49;

import bons.furious.patch.arseng_coo49.ArsEngRelays;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * arseng_generic_inv_wrapper_gate (Ars Energistique 1.2.0, LGPL-3.0: no code carried; trade-off, off by default).
 * ArsEngCapabilities.attach: its second addCapability (the generic_inv_wrapper provider every block entity gets) runs only
 * for block entity classes that override getCapability (ArsEngRelays.skipInvWrapper); the source_tile provider of source
 * tiles is untouched. With aecapfix installed, Ars Energistique's own mixin replaces that call first and this one stands
 * aside.
 */
@Mixin(targets = "gripe._90.arseng.definition.ArsEngCapabilities", remap = false)
public abstract class ArsEngInvWrapperGateMixin {
    @WrapOperation(method = "attach(Lnet/minecraftforge/event/AttachCapabilitiesEvent;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraftforge/event/AttachCapabilitiesEvent;addCapability(Lnet/minecraft/resources/ResourceLocation;Lnet/minecraftforge/common/capabilities/ICapabilityProvider;)V", ordinal = 1),
            require = 1, allow = 1)
    private static void bons$arsengInvWrapperGate(AttachCapabilitiesEvent<?> event, ResourceLocation id, ICapabilityProvider provider, Operation<Void> original) {
        if (ArsEngRelays.skipInvWrapper(event)) return;
        original.call(event, id, provider);
    }
}
