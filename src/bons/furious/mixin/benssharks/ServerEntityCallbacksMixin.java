package bons.furious.mixin.benssharks;

import bons.furious.patch.benssharks.Tracking;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * benssharks_tick_listeners (Ben's Sharks 1.3.0): onTrackingStart / onTrackingEnd bracket exactly the time an entity is in
 * the level's EntityLookup, i.e. in ServerLevel.getAllEntities() (Ad Astra, Aquamirae, Radium and Valkyrien Skies hook these
 * too; plain head/tail injects coexist). Helper: {@link Tracking}.
 */
@Mixin(targets = "net.minecraft.server.level.ServerLevel$EntityCallbacks", remap = false)
public abstract class ServerEntityCallbacksMixin {
    @Inject(method = "m_141985_(Lnet/minecraft/world/entity/Entity;)V", at = @At("TAIL"), remap = false)
    private void bons$sharksTrackingStart(Entity entity, CallbackInfo ci) {
        Tracking.start(entity);
    }

    @Inject(method = "m_141981_(Lnet/minecraft/world/entity/Entity;)V", at = @At("HEAD"), remap = false)
    private void bons$sharksTrackingEnd(Entity entity, CallbackInfo ci) {
        Tracking.end(entity);
    }
}
