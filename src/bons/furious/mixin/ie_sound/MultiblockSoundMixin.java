package bons.furious.mixin.ie_sound;

import blusunrize.immersiveengineering.common.util.sound.MultiblockSound;
import bons.furious.patch.ie_sound.IeSoundLink;
import bons.furious.patch.ie_sound.MultiblockSoundFix;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * ie_multiblock_sound_replaced (Immersive Engineering 10.2.0-183, "Blu's License of Common Sense": no IE code is carried;
 * client; tested build 10.2.0-183). Fix for IE
 * #6378: MultiblockSound remembers the instance a PlaySoundEvent handler played in its place (IeSoundLink, set by
 * SoundEngineReplacementMixin), and the supplier startSound returns (lambda$startSound$0, `soundManager.isActive(instance)`)
 * also answers true while that replacement is active. See MultiblockSoundFix.
 */
@Mixin(value = MultiblockSound.class, remap = false)
public abstract class MultiblockSoundMixin implements IeSoundLink {
    @Unique
    private SoundInstance bons$playedAs;

    @Override
    public SoundInstance bons$playedAs() {
        return this.bons$playedAs;
    }

    @Override
    public void bons$setPlayedAs(SoundInstance played) {
        this.bons$playedAs = played;
    }

    @WrapOperation(method = "lambda$startSound$0(Lnet/minecraft/client/sounds/SoundManager;Lblusunrize/immersiveengineering/common/util/sound/MultiblockSound;)Z",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/sounds/SoundManager;m_120403_(Lnet/minecraft/client/resources/sounds/SoundInstance;)Z"))
    private static boolean bons$playingOrReplaced(SoundManager manager, SoundInstance instance, Operation<Boolean> original) {
        return MultiblockSoundFix.isActive(manager, instance, original);
    }
}
