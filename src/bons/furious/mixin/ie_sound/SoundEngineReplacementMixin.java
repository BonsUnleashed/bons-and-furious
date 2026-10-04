package bons.furious.mixin.ie_sound;

import bons.furious.patch.ie_sound.MultiblockSoundFix;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * ie_multiblock_sound_replaced (Minecraft 1.20.1 + Forge 47.4.16 SoundEngine, client). In SoundEngine.play, Forge's
 * ForgeHooksClient.playSound (the PlaySoundEvent) runs as before; its result goes on unchanged, and for an Immersive
 * Engineering MultiblockSound the instance that will be played in its place is remembered (MultiblockSoundFix.link).
 * Carries none of Minecraft's code.
 */
@Mixin(value = SoundEngine.class, remap = false)
public abstract class SoundEngineReplacementMixin {
    @WrapOperation(method = "m_120312_(Lnet/minecraft/client/resources/sounds/SoundInstance;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraftforge/client/ForgeHooksClient;playSound(Lnet/minecraft/client/sounds/SoundEngine;Lnet/minecraft/client/resources/sounds/SoundInstance;)Lnet/minecraft/client/resources/sounds/SoundInstance;"))
    private SoundInstance bons$rememberReplacement(SoundEngine engine, SoundInstance sound, Operation<SoundInstance> original) {
        return MultiblockSoundFix.link(sound, original.call(engine, sound));
    }
}
