package bons.furious.mixin.client_class_warmup;

import bons.furious.patch.client_class_warmup.ClassWarmup;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * client_class_warmup (Minecraft 1.20.1 on Forge 47.4.28, client; exact variant), part 3 of 3: at the HEAD of
 * Minecraft.clearLevel(Screen) (m_91320_, leaving a world) the recorded window closes early and the warm-up stops. The
 * method runs unchanged. Helper: {@link ClassWarmup}.
 */
@Mixin(value = Minecraft.class, remap = false)
public abstract class MinecraftWarmupLeaveMixin {
    @Inject(method = "m_91320_", at = @At("HEAD"), require = 0)
    private void bons$classWarmupLeave(Screen next, CallbackInfo ci) {
        ClassWarmup.onLeave();
    }
}
