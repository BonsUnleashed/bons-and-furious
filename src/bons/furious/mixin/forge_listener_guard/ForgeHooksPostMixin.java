package bons.furious.mixin.forge_listener_guard;

import bons.furious.patch.forge_listener_guard.GuardedDispatch;
import bons.furious.patch.forge_listener_guard.Sites;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.IEventBus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * forge_listener_prologue_guard (Forge 1.20.1-47.4.28, both sides): ForgeHooks.onLivingTick, the per-entity
 * `MinecraftForge.EVENT_BUS.post(new LivingEvent.LivingTickEvent(entity))` (its result is the method's result), handed to
 * GuardedDispatch.post (MixinExtras @WrapOperation; priority 1500 and require = 0 as ForgeEventFactoryPostMixin).
 */
@Mixin(value = ForgeHooks.class, priority = 1500, remap = false)
public abstract class ForgeHooksPostMixin {
    @WrapOperation(method = "onLivingTick(Lnet/minecraft/world/entity/LivingEntity;)Z", require = 0,
            at = @At(value = "INVOKE", target = "Lnet/minecraftforge/eventbus/api/IEventBus;post(Lnet/minecraftforge/eventbus/api/Event;)Z"))
    private static boolean bons$guardLivingTick(IEventBus bus, Event event, Operation<Boolean> original) {
        return GuardedDispatch.post(Sites.LIVING_TICK, bus, event, original);
    }
}
