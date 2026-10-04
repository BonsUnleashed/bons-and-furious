package bons.furious.patch.ie_sound;

import net.minecraft.client.resources.sounds.SoundInstance;

/**
 * Added to Immersive Engineering's MultiblockSound by MultiblockSoundMixin (switch ie_multiblock_sound_replaced): the sound
 * instance the sound engine actually plays for this one when a PlaySoundEvent handler replaced it, else null.
 */
public interface IeSoundLink {
    SoundInstance bons$playedAs();

    void bons$setPlayedAs(SoundInstance played);
}
