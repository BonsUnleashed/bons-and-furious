package bons.furious.mixin.timm_c49;

import bons.furious.patch.timm_c49.TimmLogLevel;
import com.github.charlyb01.timm.Timm;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * timm_debug_log_level (The Immersive Music Mod 1.1.0, both sides, since 1.0.49; kind fix). MixinExtras @WrapOperation on the
 * one Logger.info(String) call in Timm.debugLog(String): the message goes to the same logger at DEBUG level (see
 * TimmLogLevel). TIMM is GPL-3.0; no TIMM code is carried, the wrapper only picks the log level. require = 0.
 */
@Mixin(value = Timm.class, remap = false)
public abstract class TimmDebugLevelMixin {
    @WrapOperation(method = "debugLog", require = 0, at = @At(value = "INVOKE", target = "Lorg/slf4j/Logger;info(Ljava/lang/String;)V"))
    private static void bons$timmC49DebugLevel(Logger logger, String message, Operation<Void> original) {
        TimmLogLevel.log(logger, message, original);
    }
}
