package bons.furious.mixin.konkrete_c49;

import bons.furious.patch.konkrete_c49.KonkreteLazyStart;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import de.keksuccino.konkrete.sound.SoundHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * konkrete_volume_thread_lazy_start (Konkrete 1.8.0, Apache-2.0; client side): SoundHandler.init()'s Thread.start() is
 * redirected to KonkreteLazyStart.holdOrStart (the thread is constructed exactly where stock constructs it; with the
 * switch on its start is held), and the setVolume call that ends a successful registerSound() is wrapped: after it ran,
 * the held thread starts (once). Exact variant, see KonkreteLazyStart: no volume thread runs before the first sound is
 * registered through Konkrete; the thread's first pass may set each clip's gain once more to the same value.
 */
@Mixin(value = SoundHandler.class, remap = false)
public abstract class SoundHandlerLazyStartMixin {
    @Redirect(method = "init()V", at = @At(value = "INVOKE", target = "Ljava/lang/Thread;start()V"), require = 1, allow = 1)
    private static void bons$konkreteC49HoldStart(Thread thread) {
        KonkreteLazyStart.holdOrStart(thread);
    }

    @WrapOperation(method = "registerSound(Ljava/lang/String;Ljava/lang/String;)V",
            at = @At(value = "INVOKE", target = "Lde/keksuccino/konkrete/sound/SoundHandler;setVolume(Ljava/lang/String;I)V"),
            require = 1, allow = 1)
    private static void bons$konkreteC49AfterFirstClip(String key, int percentage, Operation<Void> original) {
        original.call(key, percentage);
        KonkreteLazyStart.clipRegistered();
    }
}
