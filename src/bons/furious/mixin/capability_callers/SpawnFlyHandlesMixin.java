package bons.furious.mixin.capability_callers;

import bons.furious.patch.capability_callers.SpawnFlyHandles;
import com.ninni.spawn.server.events.ForgeEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * spawn_capability_handles (Spawn 4.0.7, licence not stated: no Spawn code is carried; both sides, since 1.0.46).
 *
 * ForgeEvents.livingTick runs event.getEntity().getCapability(FLY_CAP).ifPresent(...) for every living entity every
 * tick. That one getCapability call is redirected to SpawnFlyHandles.lookup, which returns the identical LazyOptional
 * (see CallerCapabilityHandles for why) without the dispatcher walk when the entity has kept it, and asks the entity
 * otherwise. The ifPresent call and its consumer stay Spawn's. A @Redirect allocates nothing on this path.
 */
@Mixin(value = ForgeEvents.class, remap = false)
public abstract class SpawnFlyHandlesMixin {
    @Redirect(method = "livingTick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;getCapability(Lnet/minecraftforge/common/capabilities/Capability;)Lnet/minecraftforge/common/util/LazyOptional;"))
    private static <T> LazyOptional<T> bons$keptFlyHandle(LivingEntity entity, Capability<T> capability) {
        return SpawnFlyHandles.lookup(entity, capability);
    }
}
