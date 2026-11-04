package bons.furious.mixin.capability_callers;

import bons.furious.patch.capability_callers.EeeabHandles;
import com.eeeab.eeeabsmobs.sever.handler.CapabilityHandler;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * eeeabsmobs_capability_handles (EEEAB's Mobs 0.98.1, LGPL-3.0; both sides; since 1.0.49): both entity.getCapability(cap)
 * calls of CapabilityHandler.getCapability(Entity, Capability) (the isPresent test and the orElseThrow read) answered from
 * the layer's kept handle (EeeabHandles, HandleCallers). Everything else in the method runs as shipped.
 */
@Mixin(value = CapabilityHandler.class, remap = false)
public abstract class EeeabCapabilityHandlerHandlesMixin {
    @Redirect(method = "getCapability(Lnet/minecraft/world/entity/Entity;Lnet/minecraftforge/common/capabilities/Capability;)Ljava/lang/Object;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getCapability(Lnet/minecraftforge/common/capabilities/Capability;)Lnet/minecraftforge/common/util/LazyOptional;"))
    private static <T> LazyOptional<T> bons$eeeabHandle(Entity entity, Capability<T> capability) {
        return EeeabHandles.KEY.lookup(entity, capability);
    }
}
