package bons.furious.mixin.capability_callers;

import bons.furious.patch.capability_callers.WitherHandles;
import com.alexander.witherreincarnated.capabilities.possessed.PossessedCapability;
import com.alexander.witherreincarnated.capabilities.possessed.PossessedCapabilityProvider;
import com.alexander.witherreincarnated.events.HandlePossessedMobsEvent;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.entity.living.LivingEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * witherreincarnated_possession_handles (Wither Reincarnated 1.0.5, All Rights Reserved: these injections carry none of its
 * code; both sides; since 1.0.49): the getCapability(CAP) calls of HandlePossessedMobsEvent's POSSESSED predicate (both) and
 * of possessedTick go to the layer's kept handle; possessedTick returns at once for a Mob whose possession state is idle
 * (WitherHandles.idle: its handler would change nothing). Everything else runs as shipped.
 */
@Mixin(value = HandlePossessedMobsEvent.class, remap = false)
public abstract class WitherPossessedHandlesMixin {
    @Redirect(method = "lambda$static$0(Lnet/minecraft/world/entity/Entity;)Z",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getCapability(Lnet/minecraftforge/common/capabilities/Capability;)Lnet/minecraftforge/common/util/LazyOptional;"))
    private static <T> LazyOptional<T> bons$possessedHandle(Entity entity, Capability<T> capability) {
        return WitherHandles.KEY.lookup(entity, capability);
    }

    @Redirect(method = "possessedTick(Lnet/minecraftforge/event/entity/living/LivingEvent$LivingTickEvent;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Mob;getCapability(Lnet/minecraftforge/common/capabilities/Capability;)Lnet/minecraftforge/common/util/LazyOptional;"))
    private static <T> LazyOptional<T> bons$tickHandle(Mob mob, Capability<T> capability) {
        return WitherHandles.KEY.lookup(mob, capability);
    }

    @WrapMethod(method = "possessedTick(Lnet/minecraftforge/event/entity/living/LivingEvent$LivingTickEvent;)V")
    private static void bons$idlePossession(LivingEvent.LivingTickEvent event, Operation<Void> original) {
        if (WitherHandles.KEY.enabled && WitherHandles.idleSkip && event != null) {
            LivingEntity living = event.getEntity();
            if (living instanceof Mob mob) {
                Object state = WitherHandles.KEY.lookup(mob, PossessedCapabilityProvider.CAP).orElse(null);
                if (state instanceof PossessedCapability possessed && WitherHandles.idle(possessed)) return;
            }
        }
        original.call(event);
    }
}
