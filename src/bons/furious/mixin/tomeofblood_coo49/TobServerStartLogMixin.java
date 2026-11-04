package bons.furious.mixin.tomeofblood_coo49;

import bons.furious.patch.tomeofblood_coo49.TobStartLog;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * tomeofblood_server_start_log_silence (Tome of Blood: Rebirth 0.4.5, LGPL-3.0; logical server; fix): MixinExtras
 * @WrapWithCondition on the Logger.info call of TomeOfBlood.onServerStarting, the mod template's example handler whose whole
 * body is that one line ("HELLO from server starting"): the line is no longer written. Nothing else in the method or the mod
 * reacts to it. See TobStartLog. Our own logic only.
 */
@Mixin(targets = "com.mystchonky.tomeofblood.TomeOfBlood", remap = false)
public abstract class TobServerStartLogMixin {
    @WrapWithCondition(method = "onServerStarting(Lnet/minecraftforge/event/server/ServerStartingEvent;)V",
            at = @At(value = "INVOKE", target = "Lorg/apache/logging/log4j/Logger;info(Ljava/lang/String;)V"),
            require = 1, allow = 1, remap = false)
    private boolean bons$tomeofbloodCoo49StartLine(Logger logger, String message) {
        return TobStartLog.keep(message);
    }
}
