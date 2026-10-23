package bons.furious.mixin.smartparticles_heap;

import bons.furious.patch.smartparticles_heap.SmartParticlesHeap;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * smartparticles_heap_slot_clear (Smart Particles 20.1.10), client only. [FIX]
 *
 * At the start of ParticleEngine.setLevel (setLevel; every dimension change, world join and logout) the helper
 * bons.furious.patch.smartparticles_heap.SmartParticlesHeap empties the slots of Smart Particles' particle heap and its keep
 * set, which nothing else ever clears; setLevel itself runs unchanged. No Smart Particles code is carried.
 */
@Mixin(value = ParticleEngine.class, remap = false)
public abstract class ParticleEngineHeapClearMixin {
    @Inject(method = "setLevel", at = @At("HEAD"))
    private void bons$clearSmartParticlesHeap(ClientLevel level, CallbackInfo ci) {
        SmartParticlesHeap.levelChanged(this);
    }
}
