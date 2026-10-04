package bons.furious.mixin.mowziesmobs;

import bons.furious.patch.mowziesmobs.MowzieCapabilityHandles;
import com.bobmowzie.mowziesmobs.server.capability.CapabilityHandler;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * mowziesmobs_capability_handles (Mowzie's Mobs 1.7.3, custom licence: no Mowzie's Mobs code is carried, both sides).
 *
 * CapabilityHandler.getCapability(entity, capability) returns null for a null or removed entity, otherwise
 * entity.getCapability(capability).isPresent() ? entity.getCapability(capability).orElseThrow(...) : null, so it walks
 * the entity's capability providers twice. Both entity.getCapability(capability) calls are redirected to
 * MowzieCapabilityHandles.lookup, which returns the identical LazyOptional (see that class for why) without the walk
 * when the entity has kept it, and asks the entity otherwise. Mowzie's own null and isRemoved checks, the isPresent test
 * and orElseThrow stay theirs. A redirect allocates nothing on this per-entity-per-tick path.
 */
@Mixin(value = CapabilityHandler.class, remap = false)
public abstract class CapabilityHandlerLookupMixin {
    @Redirect(method = "getCapability", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;getCapability(Lnet/minecraftforge/common/capabilities/Capability;)Lnet/minecraftforge/common/util/LazyOptional;"))
    private static <T> LazyOptional<T> bons$keptHandle(Entity entity, Capability<T> capability) {
        return MowzieCapabilityHandles.lookup(entity, capability);
    }
}
