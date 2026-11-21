package bons.furious.mixin.assetsprotector_decrypt;

import bons.furious.patch.assetsprotector_decrypt.Undecryptable;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.io.ByteArrayInputStream;
import org.spongepowered.asm.mixin.Mixin;

/**
 * assetsprotector_undecryptable_skip (Assets Protector 1.0.0, GPL-3.0; both sides): MixinExtras @WrapMethod around
 * com.min01.aes.AESUtil.decrypt(byte[]): input the decryption cannot accept (Undecryptable.cannotDecrypt) ends at once with
 * the IllegalBlockSizeException the original reaches after its key derivation; all other input runs the original. In verify
 * mode every call runs the original and the refusable ones are checked to end with that exception. No Assets Protector code
 * is carried: our own logic only.
 */
@Mixin(targets = "com.min01.aes.AESUtil", remap = false)
public abstract class AssetsProtectorPrecheckMixin {
    @WrapMethod(method = "decrypt")
    private static ByteArrayInputStream bons$apPrecheck(byte[] array, Operation<ByteArrayInputStream> original) throws Exception {
        boolean refuse = Undecryptable.cannotDecrypt(array);
        Undecryptable.count(refuse);
        if (refuse && Undecryptable.ACTIVE) throw Undecryptable.blockSize();
        if (refuse && Undecryptable.VERIFY) {
            long t0 = System.nanoTime();
            try {
                ByteArrayInputStream r = original.call(array);
                Undecryptable.verified(null, System.nanoTime() - t0);
                return r;
            } catch (Throwable t) {
                Undecryptable.verified(t, System.nanoTime() - t0);
                throw t;
            }
        }
        return original.call(array);
    }
}
