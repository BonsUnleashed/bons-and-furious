package bons.furious.mixin.mowziesmobs;

import bons.furious.patch.mowziesmobs.MowzieCapabilityHandles;
import com.bobmowzie.mowziesmobs.server.capability.CapabilityHandler;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.common.util.NonNullSupplier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * mowziesmobs_capability_handles, value form (since 1.0.46; Mowzie's Mobs 1.7.3, custom licence: no Mowzie's Mobs code
 * is carried, both sides).
 *
 * CapabilityHandler.getCapability(entity, capability) is wrapped as a whole (MixinExtras @WrapMethod): when
 * MowzieCapabilityHandles keeps the answer for this entity and capability (a value, or "absent"), Mowzie's own checks run
 * as in the original (null entity: the original runs; entity.isRemoved(): null) and the kept value (or null) is returned
 * without the two dispatcher asks, isPresent and orElseThrow. Otherwise the original runs unchanged, with the 1.0.28
 * handle redirects of CapabilityHandlerLookupMixin inside it. isRemoved is called exactly once on either path, and
 * before it only fields are read (the capability state, the dispatcher and our table).
 *
 * The original's orElseThrow call is redirected to itself plus one line: the value it returned is kept next to the
 * LazyOptional it was asked of (the target method's own entity and capability arguments are passed along). Nothing else
 * changes; an exception from orElseThrow propagates as before.
 */
@Mixin(value = CapabilityHandler.class, remap = false)
public abstract class CapabilityHandlerValueMixin {
    @WrapMethod(method = "getCapability")
    private static <T> T bons$keptValue(Entity entity, Capability<T> capability, Operation<T> original) {
        if (entity != null) {
            Object kept = MowzieCapabilityHandles.kept(entity, capability);
            if (kept != MowzieCapabilityHandles.MISS) {
                if (MowzieCapabilityHandles.SHADOW) return bons$shadow(entity, capability, kept, original);
                if (entity.m_213877_()) return null;
                return kept == MowzieCapabilityHandles.ABSENT ? null : bons$cast(kept);
            }
        }
        return original.call(entity, capability);
    }

    @Redirect(method = "getCapability", at = @At(value = "INVOKE",
            target = "Lnet/minecraftforge/common/util/LazyOptional;orElseThrow(Lnet/minecraftforge/common/util/NonNullSupplier;)Ljava/lang/Object;"))
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object bons$keepValue(LazyOptional optional, NonNullSupplier exception, Entity entity, Capability capability) {
        Object value = ((LazyOptional<Object>) optional).<RuntimeException>orElseThrow((NonNullSupplier<RuntimeException>) exception);
        MowzieCapabilityHandles.keepValue(entity, capability, optional, value);
        return value;
    }

    @SuppressWarnings("unchecked")
    private static <T> T bons$cast(Object value) {
        return (T) value;
    }

    /** Shadow verification: the original runs as well and its answer is returned; a different object is counted. */
    private static <T> T bons$shadow(Entity entity, Capability<T> capability, Object kept, Operation<T> original) {
        Object ours = kept == MowzieCapabilityHandles.ABSENT || entity.m_213877_() ? null : kept;
        return bons$cast(MowzieCapabilityHandles.shadowValue(entity, capability, ours, original.call(entity, capability)));
    }
}
