package bons.furious.mixin.cnc_coo49;

import bons.furious.patch.cnc_coo49.CncScans;
import bons.furious.patch.cnc_coo49.CncSobbingClient;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LevelAccessor;
import net.neoforged.bus.api.Event;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * cnc_sobbing_shader_own_only (Critters n' Crawlers 2.2.5 for Minecraft 1.21.1, client; gameplay fix, off by default):
 * SobbingShaderProcedure.execute, a PlayerTickEvent.Post handler on the client. Shipped, it acts for every player the client
 * ticks: a player with Sobbing loads the cry post effect when none is active, any other player shuts down whatever post
 * effect is active (spectator creeper / spider / enderman vision, other mods' effects), every tick. With the switch the
 * handler returns at once for players other than the local one, and the shutdown happens only when the active effect is
 * the mod's own cry.json (CncSobbingClient.isCry); loading is unchanged. See CncScans.sobbingOtherPlayer.
 */
@Mixin(targets = "net.imasillylittleguy.cnc.procedures.SobbingShaderProcedure", remap = false)
public abstract class SobbingShaderOwnMixin {
    private static final String EXECUTE = "execute(Lnet/neoforged/bus/api/Event;Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/world/entity/Entity;)V";

    @Inject(method = EXECUTE, at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    private static void bons$cncCoo49SobbingLocalOnly(Event event, LevelAccessor world, Entity entity, CallbackInfo ci) {
        if (CncScans.sobbingOtherPlayer(world, entity)) ci.cancel();
    }

    @WrapOperation(method = EXECUTE, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;shutdownEffect()V"),
            require = 1, allow = 1)
    private static void bons$cncCoo49SobbingOwnShutdown(GameRenderer renderer, Operation<Void> original) {
        if (CncScans.sobbing && !CncSobbingClient.isCry(renderer.currentEffect())) return;
        original.call(renderer);
    }
}
