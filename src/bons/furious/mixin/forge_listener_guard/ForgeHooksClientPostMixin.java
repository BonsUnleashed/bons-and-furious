package bons.furious.mixin.forge_listener_guard;

import bons.furious.patch.forge_listener_guard.GuardedDispatch;
import bons.furious.patch.forge_listener_guard.Sites;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.IEventBus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * forge_listener_prologue_guard (Forge 1.20.1-47.4.28, client): ForgeHooksClient.dispatchRenderStage(Stage, ...), the
 * `MinecraftForge.EVENT_BUS.post(new RenderLevelStageEvent(...))` of every render stage (the RenderType overload calls
 * this one), handed to GuardedDispatch.post (MixinExtras @WrapOperation; priority 1500 and require = 0 as
 * ForgeEventFactoryPostMixin).
 */
@Mixin(value = ForgeHooksClient.class, priority = 1500, remap = false)
public abstract class ForgeHooksClientPostMixin {
    @WrapOperation(method = "dispatchRenderStage(Lnet/minecraftforge/client/event/RenderLevelStageEvent$Stage;Lnet/minecraft/client/renderer/LevelRenderer;"
            + "Lcom/mojang/blaze3d/vertex/PoseStack;Lorg/joml/Matrix4f;ILnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/culling/Frustum;)V",
            require = 0, at = @At(value = "INVOKE", target = "Lnet/minecraftforge/eventbus/api/IEventBus;post(Lnet/minecraftforge/eventbus/api/Event;)Z"))
    private static boolean bons$guardRenderStage(IEventBus bus, Event event, Operation<Boolean> original) {
        return GuardedDispatch.post(Sites.RENDER_STAGE, bus, event, original);
    }
}
