package bons.furious.mixin.capability_callers;

import bons.furious.patch.capability_callers.L2HostilityHandles;
import dev.xkmc.l2hostility.events.CapabilityEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * l2hostility_capability_handles (L2 Hostility 2.5.19, All Rights Reserved: this injection carries none of its code; both
 * sides; since 1.0.49): the entity.getCapability(MobTraitCap.CAPABILITY) call of CapabilityEvents.livingTickEvent answered
 * from the layer's kept handle (L2HostilityHandles, HandleCallers). Everything else runs as shipped.
 */
@Mixin(value = CapabilityEvents.class, remap = false)
public abstract class L2HostilityTickHandlesMixin {
    @Redirect(method = "livingTickEvent(Lnet/minecraftforge/event/entity/living/LivingEvent$LivingTickEvent;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getCapability(Lnet/minecraftforge/common/capabilities/Capability;)Lnet/minecraftforge/common/util/LazyOptional;"))
    private static <T> LazyOptional<T> bons$traitHandle(LivingEntity entity, Capability<T> capability) {
        return L2HostilityHandles.KEY.lookup(entity, capability);
    }
}
