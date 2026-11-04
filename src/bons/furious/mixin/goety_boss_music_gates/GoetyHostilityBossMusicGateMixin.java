package bons.furious.mixin.goety_boss_music_gates;

import bons.furious.patch.goety_boss_music_gates.GoetyMusicGates;
import net.minecraftforge.event.entity.living.LivingEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * goetyhostility_boss_music_class_gate (Goety Hostility 1.4.4, All Rights Reserved: no code carried; client). GHClientForgeEvents.onEntityTick
 * runs for every living entity every client tick and acts only for a HostileWildfire (its boss theme); for any other entity
 * it returns at its start (its reads before that have no effect). See GoetyMusicGates.
 */
@Mixin(targets = "com.ratrod.goetyhostility.client.event.GHClientForgeEvents", remap = false)
public abstract class GoetyHostilityBossMusicGateMixin {
    @Inject(method = "onEntityTick(Lnet/minecraftforge/event/entity/living/LivingEvent$LivingTickEvent;)V", at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    private static void bons$classGate(LivingEvent.LivingTickEvent event, CallbackInfo ci) {
        if (GoetyMusicGates.skipHostility(event.getEntity())) ci.cancel();
    }
}
