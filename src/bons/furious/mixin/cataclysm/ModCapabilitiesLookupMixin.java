package bons.furious.mixin.cataclysm;

import bons.furious.patch.cataclysm.CataclysmCapabilityHandles;
import com.github.L_Ender.cataclysm.init.ModCapabilities;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * cataclysm_capability_handles (L_Ender's Cataclysm 3.16, CC-BY-NC-ND-4.0: no Cataclysm code is carried; both sides).
 *
 * ModCapabilities.getCapability(entity, capability) returns null for a null or not-alive entity, otherwise
 * entity.getCapability(capability).isPresent() ? entity.getCapability(capability).orElseThrow(...) : null, so it walks
 * the entity's capability providers twice. Both entity.getCapability(capability) calls are redirected to
 * CataclysmCapabilityHandles.lookup, which returns the identical LazyOptional without the walk when the entity has kept
 * it, and asks the entity otherwise. Cataclysm's own null and isAlive checks, the isPresent test and orElseThrow stay
 * theirs. A @Redirect allocates nothing on this per-entity-per-tick path.
 */
@Mixin(value = ModCapabilities.class, remap = false)
public abstract class ModCapabilitiesLookupMixin {
    @Redirect(method = "getCapability", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;getCapability(Lnet/minecraftforge/common/capabilities/Capability;)Lnet/minecraftforge/common/util/LazyOptional;"))
    private static <T> LazyOptional<T> bons$keptTokenHandle(Entity entity, Capability<T> capability) {
        return CataclysmCapabilityHandles.lookup(entity, capability);
    }
}
