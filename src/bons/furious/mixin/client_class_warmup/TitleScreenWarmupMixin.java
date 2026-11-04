package bons.furious.mixin.client_class_warmup;

import bons.furious.patch.client_class_warmup.ClassWarmup;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * client_class_warmup (Minecraft 1.20.1 on Forge 47.4.28, client; exact variant), part 1 of 3: after TitleScreen.init
 * (m_7856_) the background warm-up starts (once per launch) and the class-define recorder is made ready for the next join.
 * The method runs unchanged. Helper: {@link ClassWarmup}.
 */
@Mixin(value = TitleScreen.class, remap = false)
public abstract class TitleScreenWarmupMixin {
    @Inject(method = "m_7856_", at = @At("RETURN"), require = 0)
    private void bons$classWarmupTitle(CallbackInfo ci) {
        ClassWarmup.onTitleScreen();
    }
}
