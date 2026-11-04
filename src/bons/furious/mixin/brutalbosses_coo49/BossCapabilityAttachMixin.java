package bons.furious.mixin.brutalbosses_coo49;

import bons.furious.patch.brutalbosses_coo49.BossCaps;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * brutalbosses_living_boss_capability (Brutal Bosses 8.5, All Rights Reserved; a client mixin: a dedicated server never
 * loads it; exact variant): @Inject at HEAD (cancellable) of EventHandler.attachCapabilities (the AttachCapabilitiesEvent
 * listener that runs for every entity object built): on a client level an entity that is not a LivingEntity gets no
 * BossCapability (the original attaches one to every client entity; only living entities can ever be bosses). Server
 * levels (the integrated server's included) run the original. Our own logic only; nothing of the mod is carried.
 */
@Mixin(targets = "com.brutalbosses.event.EventHandler", remap = false)
public abstract class BossCapabilityAttachMixin {
    @Inject(method = "attachCapabilities(Lnet/minecraftforge/event/AttachCapabilitiesEvent;)V", at = @At("HEAD"), cancellable = true,
            require = 1, allow = 1)
    private static void bons$brutalbossesCoo49ClientSkip(AttachCapabilitiesEvent<Entity> event, CallbackInfo ci) {
        if (BossCaps.skipOnClient(event)) ci.cancel();
    }
}
