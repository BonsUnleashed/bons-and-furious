package bons.furious.mixin.client_class_warmup;

import bons.furious.patch.client_class_warmup.ClassWarmup;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * client_class_warmup (Minecraft 1.21.1 on NeoForge 21.1.252, client; exact variant), part 1 of 3: after TitleScreen.init
 * the background warm-up starts (once per launch) and the class-define recorder is made ready for the next join. The
 * method runs unchanged. Helper: {@link ClassWarmup}.
 */
@Mixin(value = TitleScreen.class, remap = false)
public abstract class TitleScreenWarmupMixin {
    @Inject(method = "init()V", at = @At("RETURN"), require = 0)
    private void bons$classWarmupTitle(CallbackInfo ci) {
        ClassWarmup.onTitleScreen();
    }
}
