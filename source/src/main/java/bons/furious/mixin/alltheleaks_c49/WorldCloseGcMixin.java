package bons.furious.mixin.alltheleaks_c49;

import bons.furious.patch.alltheleaks_c49.AtlLean;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * alltheleaks_world_close_gc_background (AllTheLeaks 1.1.14 for 1.21.1, MIT; client mixin, so the client and single player
 * (the integrated server); a trade-off, off by default): MixinExtras @WrapOperation on the two calls of
 * CommonEvents.onClosedServer (ServerStoppedEvent): MemoryMonitor.runExplicitGc() and Trackable.clearNullReferences() run,
 * in that order, on one daemon thread instead of the stopping thread (AtlLean.worldCloseGc / worldCloseClear); the
 * SERVER_STOP statistic after them still counts on the caller. CommonEvents also names client classes, so a dedicated server
 * does not get this mixin and keeps the collection on its stopping thread.
 */
@Mixin(targets = "dev.uncandango.alltheleaks.events.CommonEvents", remap = false)
public abstract class WorldCloseGcMixin {
    @WrapOperation(method = "onClosedServer(Lnet/neoforged/neoforge/event/server/ServerStoppedEvent;)V", at = @At(value = "INVOKE",
            target = "Ldev/uncandango/alltheleaks/feature/common/mods/minecraft/MemoryMonitor;runExplicitGc()Z"))
    private static boolean bons$atlCloseGc(Operation<Boolean> original) {
        return AtlLean.worldCloseGc(original);
    }

    @WrapOperation(method = "onClosedServer(Lnet/neoforged/neoforge/event/server/ServerStoppedEvent;)V", at = @At(value = "INVOKE",
            target = "Ldev/uncandango/alltheleaks/mixin/Trackable;clearNullReferences()V"))
    private static void bons$atlCloseClear(Operation<Void> original) {
        AtlLean.worldCloseClear(original);
    }
}
