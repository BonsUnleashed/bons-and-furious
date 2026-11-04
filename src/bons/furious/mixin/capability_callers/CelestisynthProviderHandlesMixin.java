package bons.furious.mixin.capability_callers;

import bons.furious.patch.capability_callers.CelestisynthHandles;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.thecelestialworkshop.celestisynth.common.capabilities.CSEntityCapabilityProvider;

/**
 * celestisynth_capability_handles (Celestisynth 1.3.4, MIT; both sides; since 1.0.49): the entity.getCapability(CAPABILITY)
 * call of CSEntityCapabilityProvider.get(LivingEntity) answered from the layer's kept handle (CelestisynthHandles,
 * HandleCallers).
 */
@Mixin(value = CSEntityCapabilityProvider.class, remap = false)
public abstract class CelestisynthProviderHandlesMixin {
    @Redirect(method = "get(Lnet/minecraft/world/entity/LivingEntity;)Lnet/minecraftforge/common/util/LazyOptional;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getCapability(Lnet/minecraftforge/common/capabilities/Capability;)Lnet/minecraftforge/common/util/LazyOptional;"))
    private static <T> LazyOptional<T> bons$celestisynthHandle(LivingEntity entity, Capability<T> capability) {
        return CelestisynthHandles.KEY.lookup(entity, capability);
    }
}
