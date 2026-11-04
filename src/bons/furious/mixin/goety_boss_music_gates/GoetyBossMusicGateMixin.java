package bons.furious.mixin.goety_boss_music_gates;

import bons.furious.patch.goety_boss_music_gates.GoetyMusicGates;
import net.minecraftforge.event.entity.living.LivingEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * goety_boss_music_class_gate (Goety 2.5.60.0 / 2.5.58.4, MIT: no code carried; client). ClientEvents.onEntityTick
 * runs for every living entity every client tick and acts only for entities of 14 Goety classes; for any other entity it
 * returns at its start (its reads before that have no effect). See GoetyMusicGates.
 */
@Mixin(targets = "com.Polarice3.Goety.client.events.ClientEvents", remap = false)
public abstract class GoetyBossMusicGateMixin {
    @Inject(method = "onEntityTick(Lnet/minecraftforge/event/entity/living/LivingEvent$LivingTickEvent;)V", at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    private static void bons$classGate(LivingEvent.LivingTickEvent event, CallbackInfo ci) {
        if (GoetyMusicGates.skipGoety(event.getEntity())) ci.cancel();
    }
}
