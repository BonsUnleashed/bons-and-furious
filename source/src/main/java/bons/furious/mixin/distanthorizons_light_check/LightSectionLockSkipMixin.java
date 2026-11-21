package bons.furious.mixin.distanthorizons_light_check;

import bons.furious.patch.distanthorizons_light_check.LightCheck;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.concurrent.locks.ReentrantLock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * distanthorizons_light_section_lock_skip (Distant Horizons 3.3.3 for Minecraft 1.21.1, LGPL-3.0; both sides; off by default;
 * ported from the Forge switch for Distant Horizons 3.3.2, whose ChunkLightStorage$LightSection class file is byte-identical
 * to 3.3.3's for 1.21.1): MixinExtras @WrapOperation
 * on the concurrencyCheckLock.tryLock() call and on every unlock() call of ChunkLightStorage$LightSection.get / set. Both are
 * answered without the lock (see LightCheck); with -Dbons_and_furious.dhLightCheck=keep both run as shipped. No Distant Horizons
 * code is carried: our own logic only.
 */
@Mixin(targets = "com.seibel.distanthorizons.core.wrapperInterfaces.chunk.ChunkLightStorage$LightSection", remap = false)
public abstract class LightSectionLockSkipMixin {
    @WrapOperation(method = {"get", "set"}, require = 2, allow = 2, at = @At(value = "INVOKE", target = "Ljava/util/concurrent/locks/ReentrantLock;tryLock()Z"))
    private boolean bons$dhTryLock(ReentrantLock lock, Operation<Boolean> original) {
        if (LightCheck.SKIP && LightCheck.announce()) return true;
        return original.call(lock);
    }

    @WrapOperation(method = {"get", "set"}, require = 2, at = @At(value = "INVOKE", target = "Ljava/util/concurrent/locks/ReentrantLock;unlock()V"))
    private void bons$dhUnlock(ReentrantLock lock, Operation<Void> original) {
        if (!LightCheck.SKIP) original.call(lock);
    }
}
