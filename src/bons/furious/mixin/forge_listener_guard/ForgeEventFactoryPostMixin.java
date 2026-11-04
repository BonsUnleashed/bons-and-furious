package bons.furious.mixin.forge_listener_guard;

import bons.furious.patch.forge_listener_guard.GuardedDispatch;
import bons.furious.patch.forge_listener_guard.Sites;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.IEventBus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * forge_listener_prologue_guard (Forge 1.20.1-47.4.28, both sides): the ten tick posts of ForgeEventFactory
 * (onPre/PostClientTick, onPlayerPre/PostTick, onPre/PostServerTick, onPre/PostLevelTick, onRenderTickStart/End). Each
 * method's single MinecraftForge.EVENT_BUS.post(new TickEvent...) is handed to GuardedDispatch.post with its site
 * number (MixinExtras @WrapOperation; the original operation is called whenever the switch does not replay the post).
 * Priority 1500, above the default: Mixin lets the wrapper into another mod's @Overwrite of the method instead of
 * refusing it at start; the group's plugin (ListenerGuardPlugin) finds any other mod's mixin on these methods and makes
 * that site stand down. require = 0: a post call another mod has already replaced is left to that mod.
 */
@Mixin(value = ForgeEventFactory.class, priority = 1500, remap = false)
public abstract class ForgeEventFactoryPostMixin {
    @WrapOperation(method = "onPreClientTick()V", require = 0,
            at = @At(value = "INVOKE", target = "Lnet/minecraftforge/eventbus/api/IEventBus;post(Lnet/minecraftforge/eventbus/api/Event;)Z"))
    private static boolean bons$guardPreClientTick(IEventBus bus, Event event, Operation<Boolean> original) {
        return GuardedDispatch.post(Sites.PRE_CLIENT_TICK, bus, event, original);
    }

    @WrapOperation(method = "onPostClientTick()V", require = 0,
            at = @At(value = "INVOKE", target = "Lnet/minecraftforge/eventbus/api/IEventBus;post(Lnet/minecraftforge/eventbus/api/Event;)Z"))
    private static boolean bons$guardPostClientTick(IEventBus bus, Event event, Operation<Boolean> original) {
        return GuardedDispatch.post(Sites.POST_CLIENT_TICK, bus, event, original);
    }

    @WrapOperation(method = "onPlayerPreTick(Lnet/minecraft/world/entity/player/Player;)V", require = 0,
            at = @At(value = "INVOKE", target = "Lnet/minecraftforge/eventbus/api/IEventBus;post(Lnet/minecraftforge/eventbus/api/Event;)Z"))
    private static boolean bons$guardPlayerPreTick(IEventBus bus, Event event, Operation<Boolean> original) {
        return GuardedDispatch.post(Sites.PLAYER_PRE_TICK, bus, event, original);
    }

    @WrapOperation(method = "onPlayerPostTick(Lnet/minecraft/world/entity/player/Player;)V", require = 0,
            at = @At(value = "INVOKE", target = "Lnet/minecraftforge/eventbus/api/IEventBus;post(Lnet/minecraftforge/eventbus/api/Event;)Z"))
    private static boolean bons$guardPlayerPostTick(IEventBus bus, Event event, Operation<Boolean> original) {
        return GuardedDispatch.post(Sites.PLAYER_POST_TICK, bus, event, original);
    }

    @WrapOperation(method = "onPreServerTick(Ljava/util/function/BooleanSupplier;Lnet/minecraft/server/MinecraftServer;)V", require = 0,
            at = @At(value = "INVOKE", target = "Lnet/minecraftforge/eventbus/api/IEventBus;post(Lnet/minecraftforge/eventbus/api/Event;)Z"))
    private static boolean bons$guardPreServerTick(IEventBus bus, Event event, Operation<Boolean> original) {
        return GuardedDispatch.post(Sites.PRE_SERVER_TICK, bus, event, original);
    }

    @WrapOperation(method = "onPostServerTick(Ljava/util/function/BooleanSupplier;Lnet/minecraft/server/MinecraftServer;)V", require = 0,
            at = @At(value = "INVOKE", target = "Lnet/minecraftforge/eventbus/api/IEventBus;post(Lnet/minecraftforge/eventbus/api/Event;)Z"))
    private static boolean bons$guardPostServerTick(IEventBus bus, Event event, Operation<Boolean> original) {
        return GuardedDispatch.post(Sites.POST_SERVER_TICK, bus, event, original);
    }

    @WrapOperation(method = "onPreLevelTick(Lnet/minecraft/world/level/Level;Ljava/util/function/BooleanSupplier;)V", require = 0,
            at = @At(value = "INVOKE", target = "Lnet/minecraftforge/eventbus/api/IEventBus;post(Lnet/minecraftforge/eventbus/api/Event;)Z"))
    private static boolean bons$guardPreLevelTick(IEventBus bus, Event event, Operation<Boolean> original) {
        return GuardedDispatch.post(Sites.PRE_LEVEL_TICK, bus, event, original);
    }

    @WrapOperation(method = "onPostLevelTick(Lnet/minecraft/world/level/Level;Ljava/util/function/BooleanSupplier;)V", require = 0,
            at = @At(value = "INVOKE", target = "Lnet/minecraftforge/eventbus/api/IEventBus;post(Lnet/minecraftforge/eventbus/api/Event;)Z"))
    private static boolean bons$guardPostLevelTick(IEventBus bus, Event event, Operation<Boolean> original) {
        return GuardedDispatch.post(Sites.POST_LEVEL_TICK, bus, event, original);
    }

    @WrapOperation(method = "onRenderTickStart(F)V", require = 0,
            at = @At(value = "INVOKE", target = "Lnet/minecraftforge/eventbus/api/IEventBus;post(Lnet/minecraftforge/eventbus/api/Event;)Z"))
    private static boolean bons$guardRenderTickStart(IEventBus bus, Event event, Operation<Boolean> original) {
        return GuardedDispatch.post(Sites.RENDER_TICK_START, bus, event, original);
    }

    @WrapOperation(method = "onRenderTickEnd(F)V", require = 0,
            at = @At(value = "INVOKE", target = "Lnet/minecraftforge/eventbus/api/IEventBus;post(Lnet/minecraftforge/eventbus/api/Event;)Z"))
    private static boolean bons$guardRenderTickEnd(IEventBus bus, Event event, Operation<Boolean> original) {
        return GuardedDispatch.post(Sites.RENDER_TICK_END, bus, event, original);
    }
}
