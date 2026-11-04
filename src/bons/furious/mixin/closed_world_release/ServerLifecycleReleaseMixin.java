package bons.furious.mixin.closed_world_release;

import bons.furious.patch.closed_world_release.ClosedWorldRelease;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * closed_world_reference_release (Forge 47.4.28, server side incl. the integrated server; since 1.0.49). Fix.
 *  - ServerLifecycleHooks.handleServerStopping, at its end (after the ServerStoppingEvent listeners): Simply Swords' sound
 *    loops are stopped the way its own stopLoopingSound stops one.
 *  - ServerLifecycleHooks.handleServerStopped, right before it clears currentServer (after every ServerStoppedEvent listener
 *    and before the exit latch lets a waiting thread go on): references other mods keep to the stopped server's levels are
 *    released (Opposing Force's skyvern spawner map, xaerolib's config managers).
 * Both methods run unchanged; the release is ClosedWorldRelease (reflection only; no target mod's code is carried). Once per
 * server stop, so @Inject's CallbackInfo does not matter.
 */
@Mixin(value = ServerLifecycleHooks.class, remap = false)
public abstract class ServerLifecycleReleaseMixin {
    @Inject(method = "handleServerStopping", at = @At("TAIL"), remap = false, require = 1, allow = 1)
    private static void bons$releaseOnStopping(MinecraftServer server, CallbackInfo ci) {
        ClosedWorldRelease.serverStopping(server);
    }

    @Inject(method = "handleServerStopped", remap = false, require = 1, allow = 1, at = @At(value = "FIELD", opcode = Opcodes.PUTSTATIC,
            target = "Lnet/minecraftforge/server/ServerLifecycleHooks;currentServer:Lnet/minecraft/server/MinecraftServer;"))
    private static void bons$releaseOnStopped(MinecraftServer server, CallbackInfo ci) {
        ClosedWorldRelease.serverStopped(server);
    }
}
