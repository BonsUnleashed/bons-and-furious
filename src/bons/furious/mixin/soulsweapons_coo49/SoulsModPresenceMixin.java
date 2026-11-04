package bons.furious.mixin.soulsweapons_coo49;

import bons.furious.patch.soulsweapons_coo49.SoulsModPresence;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.Mixin;

/**
 * soulsweapons_mod_presence_memo (Soulslike Weaponry 1.4.10, same bytes in 1.4.8; CC0-1.0; both sides; since 1.0.49).
 * MixinExtras @WrapMethod on WeaponUtil.isModLoaded(String) and WeaponUtil.isFightModLoaded(): the answer is kept per mod
 * id string object (and for the fight-mod question once) together with the FML loading mod list it was read from, and
 * handed out again while FMLLoader still holds that same list (its file-by-id map is built once, in its constructor, and
 * never changed). Without a list (the early loader phase) the original runs. Our own logic only; nothing of the mod is
 * carried.
 */
@Mixin(targets = "net.soulsweaponry.util.WeaponUtil", remap = false)
public abstract class SoulsModPresenceMixin {
    @WrapMethod(method = "isModLoaded(Ljava/lang/String;)Z", remap = false)
    private static boolean bons$soulsweaponsCoo49ModLoaded(String modId, Operation<Boolean> original) {
        return SoulsModPresence.isModLoaded(modId, original);
    }

    @WrapMethod(method = "isFightModLoaded()Z", remap = false)
    private static boolean bons$soulsweaponsCoo49FightModLoaded(Operation<Boolean> original) {
        return SoulsModPresence.isFightModLoaded(original);
    }
}
