package bons.furious.mixin.capability_callers;

import bons.furious.patch.capability_callers.BotaniaLooniumHandles;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import vazkii.botania.forge.xplat.ForgeXplatImpl;

/**
 * botania_capability_handles (Botania 1.20.1-456, Botania licence: no Botania code is carried; both sides, since 1.0.46).
 *
 * ForgeXplatImpl.looniumComponent(entity) is entity.getCapability(LOONIUM_DROP).orElse(null). That getCapability call is
 * redirected to BotaniaLooniumHandles.lookup, which returns the identical LazyOptional (or LazyOptional.empty() for an
 * entity without the capability; see CallerCapabilityHandles for why) without the dispatcher walk when the entity has
 * kept it, and asks the entity otherwise. orElse stays Botania's. A @Redirect allocates nothing on this path.
 */
@Mixin(value = ForgeXplatImpl.class, remap = false)
public abstract class BotaniaLooniumHandlesMixin {
    @Redirect(method = "looniumComponent", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;getCapability(Lnet/minecraftforge/common/capabilities/Capability;)Lnet/minecraftforge/common/util/LazyOptional;"))
    private <T> LazyOptional<T> bons$keptLooniumHandle(LivingEntity entity, Capability<T> capability) {
        return BotaniaLooniumHandles.lookup(entity, capability);
    }
}
