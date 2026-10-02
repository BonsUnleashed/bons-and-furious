package bons.furious.mixin.relics;

import bons.furious.patch.relics.RelicsForeignCapability;
import it.hurts.sskirillss.relics.capability.entries.IRelicsCapability;
import it.hurts.sskirillss.relics.init.CapabilityRegistry;
import net.minecraft.core.Direction;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * relics_foreign_capability_fast_path (Relics 1.20.1-0.8.0.13, both sides).
 *
 * RelicsCapabilityProvider.getCapability returned CapabilityRegistry.DATA.orEmpty(cap, LazyOptional.of(() -> backend)).
 * Forge's Capability.orEmpty is `this == toCheck ? inst.cast() : LazyOptional.empty()`, so for any capability other than
 * DATA (null included) the result is the shared LazyOptional.empty() singleton, and the LazyOptional, its lock, its
 * listener set and the supplier lambda built before it are garbage; constructing them has no side effect and the lambda
 * is never called on that path. A query for any other capability now returns LazyOptional.empty() at the head of the
 * method: the identical object. DATA queries fall through to Relics' original code unchanged (a fresh LazyOptional each
 * call, as before; it is deliberately not cached, because Relics registers no invalidation listener for one). DATA is a
 * static final Capability that Forge's CapabilityManager creates when CapabilityRegistry initialises, never null.
 * Relics is All Rights Reserved: this mixin carries none of its code, only the identity check at the head.
 */
@Mixin(value = IRelicsCapability.RelicsCapabilityProvider.class, remap = false)
public abstract class ForeignCapabilityFastPathMixin {
    @Inject(method = "getCapability", at = @At("HEAD"), cancellable = true)
    private <T> void bons$foreignCapability(Capability<T> cap, Direction side, CallbackInfoReturnable<LazyOptional<T>> cir) {
        if (cap != CapabilityRegistry.DATA && RelicsForeignCapability.enabled) cir.setReturnValue(LazyOptional.empty());
    }
}
