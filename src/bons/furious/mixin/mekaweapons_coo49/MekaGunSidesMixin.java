package bons.furious.mixin.mekaweapons_coo49;

import bons.furious.patch.mekaweapons_coo49.MekaWeaponsFixes;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import mekanism.common.registration.impl.SoundEventRegistryObject;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/**
 * mekaweapons_gun_sound_client_only (Mekanism Weapons 4.2.1; the Meka-Gun does not exist in 3.0: string target + @Pseudo,
 * nothing here names a 4.x-only class, and the key is ABSENT with 3.0): WrapOperation on the SoundHandler.playSound(LASER)
 * call in ItemMekaGun.use, which now runs only on a client-side level (no NoClassDefFoundError on a dedicated server, one
 * sound per shot in singleplayer). See MekaWeaponsFixes. COO 5.8 hooks the same method (mekaweapons.MixinMekaGunServerSafety,
 * option mekaweapons.gunClientOnlySound); a WrapOperation composes with another mod's wrap of the call.
 */
@Pseudo
@Mixin(targets = "meranha.mekaweapons.items.ItemMekaGun", remap = false)
public abstract class MekaGunSidesMixin {
    @WrapOperation(method = "m_7203_", at = @At(value = "INVOKE",
            target = "Lmekanism/client/sound/SoundHandler;playSound(Lmekanism/common/registration/impl/SoundEventRegistryObject;)V"),
            require = 1, allow = 1)
    private void bons$gunSoundClientOnly(SoundEventRegistryObject<?> sound, Operation<Void> original, @Local(argsOnly = true) Level level) {
        if (!MekaWeaponsFixes.gunSound || level.f_46443_) {
            original.call(sound);
            return;
        }
        MekaWeaponsFixes.announce(1, "mekaweapons_gun_sound_client_only", "the Meka-Gun shot sound plays on the shooter's client only");
    }
}
