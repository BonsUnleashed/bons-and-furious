package bons.furious.mixin.goety_boss_music_gates;

import bons.furious.patch.goety_boss_music_gates.GoetyMusicGates;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * goety_boss_music_class_gate (Goety 3.2.2, MIT: no code carried; client). ClientEvents.onEntityTick (EntityTickEvent.Post)
 * runs for every entity every tick and acts only for entities of 14 Goety classes; for any other entity it returns at its
 * start, after the one write the check makes for it (Goety's misc attachment of a living entity on a client level while
 * the boss-music option is on). See GoetyMusicGates.
 */
@Mixin(targets = "com.Polarice3.Goety.client.events.ClientEvents", remap = false)
public abstract class GoetyBossMusicGateMixin {
    @Inject(method = "onEntityTick(Lnet/neoforged/neoforge/event/tick/EntityTickEvent$Post;)V", at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    private static void bons$classGate(EntityTickEvent.Post event, CallbackInfo ci) {
        if (GoetyMusicGates.skipGoety(event.getEntity())) ci.cancel();
    }
}
