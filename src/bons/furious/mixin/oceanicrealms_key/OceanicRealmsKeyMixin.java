package bons.furious.mixin.oceanicrealms_key;

import bons.furious.patch.oceanicrealms_key.MasterKey;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import javax.crypto.SecretKey;
import org.spongepowered.asm.mixin.Mixin;

/**
 * oceanicrealms_master_key_once (Oceanic Realms 1.0.7, All Rights Reserved; both sides): MixinExtras @WrapMethod around
 * com.min01.oceanicrealms.AESUtil.getMasterKey: the first key derived is kept and returned again (see MasterKey). No Oceanic
 * Realms code is carried: our own logic only.
 */
@Mixin(targets = "com.min01.oceanicrealms.AESUtil", remap = false)
public abstract class OceanicRealmsKeyMixin {
    @WrapMethod(method = "getMasterKey")
    private static SecretKey bons$orMasterKey(Operation<SecretKey> original) {
        return MasterKey.get(original);
    }
}
