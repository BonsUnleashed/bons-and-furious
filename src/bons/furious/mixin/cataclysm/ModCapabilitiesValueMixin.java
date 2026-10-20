package bons.furious.mixin.cataclysm;

import bons.furious.patch.cataclysm.CataclysmCapabilityHandles;
import bons.furious.patch.cataclysm.PrivateCapabilityHandles;
import com.github.L_Ender.cataclysm.init.ModCapabilities;
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
 * cataclysm_capability_handles, value form (since 1.0.46; L_Ender's Cataclysm 3.16, CC-BY-NC-ND-4.0: no Cataclysm code is
 * carried; both sides).
 *
 * ModCapabilities.getCapability(entity, capability) is wrapped as a whole (MixinExtras @WrapMethod): when the shared
 * PrivateCapabilityHandles table keeps the answer for this entity and capability (a value, or "absent"), Cataclysm's own
 * checks run as in the original (null entity: the original runs; !entity.isAlive(): null) and the kept value (or null)
 * is returned without the two dispatcher asks, isPresent and orElseThrow. Otherwise the original runs unchanged, with the
 * 1.0.36 handle redirects of ModCapabilitiesLookupMixin inside it. isAlive is called exactly once on either path, and
 * before it only fields are read (the capability state, the dispatcher and our table).
 *
 * The original's orElseThrow call is redirected to itself plus one line: the value it returned is kept next to the
 * LazyOptional it was asked of (the target method's own entity and capability arguments are passed along). Nothing else
 * changes; an exception from orElseThrow propagates as before.
 */
@Mixin(value = ModCapabilities.class, remap = false)
public abstract class ModCapabilitiesValueMixin {
    @WrapMethod(method = "getCapability")
    private static <T> T bons$keptTokenValue(Entity entity, Capability<T> capability, Operation<T> original) {
        if (entity != null) {
            Object kept = CataclysmCapabilityHandles.kept(entity, capability);
            if (kept != PrivateCapabilityHandles.MISS) {
                if (CataclysmCapabilityHandles.SHADOW) return bons$shadow(entity, capability, kept, original);
                if (!entity.m_6084_()) return null;
                return kept == PrivateCapabilityHandles.ABSENT ? null : bons$cast(kept);
            }
        }
        return original.call(entity, capability);
    }

    @Redirect(method = "getCapability", at = @At(value = "INVOKE",
            target = "Lnet/minecraftforge/common/util/LazyOptional;orElseThrow(Lnet/minecraftforge/common/util/NonNullSupplier;)Ljava/lang/Object;"))
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object bons$keepTokenValue(LazyOptional optional, NonNullSupplier exception, Entity entity, Capability capability) {
        Object value = ((LazyOptional<Object>) optional).<RuntimeException>orElseThrow((NonNullSupplier<RuntimeException>) exception);
        CataclysmCapabilityHandles.keepValue(entity, capability, optional, value);
        return value;
    }

    @SuppressWarnings("unchecked")
    private static <T> T bons$cast(Object value) {
        return (T) value;
    }

    /** Shadow verification: the original runs as well and its answer is returned; a different object is counted. */
    private static <T> T bons$shadow(Entity entity, Capability<T> capability, Object kept, Operation<T> original) {
        Object ours = kept == PrivateCapabilityHandles.ABSENT || !entity.m_6084_() ? null : kept;
        return bons$cast(CataclysmCapabilityHandles.shadowValue(entity, capability, ours, original.call(entity, capability)));
    }
}
