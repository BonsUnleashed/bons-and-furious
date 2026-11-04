package bons.furious.mixin.subtleeffects_c49;

import bons.furious.patch.subtleeffects_c49.DisplayTickStateCache;
import einstein.subtle_effects.init.ModBlockTickers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * subtleeffects_display_tick_state_cache (Subtle Effects 1.15.1-hotfix.1, client; off by default): ModBlockTickers.init
 * clears and refills the special-ticker map (at start-up and whenever Subtle Effects' block settings are saved), so the
 * per-state answers kept by DisplayTickStateCache are dropped at its start. Our own logic only.
 */
@Mixin(value = ModBlockTickers.class, remap = false)
public abstract class TickerInitCacheResetMixin {
    @Inject(method = "init()V", at = @At("HEAD"), require = 1, allow = 1)
    private static void bons$seCacheReset(CallbackInfo ci) {
        DisplayTickStateCache.invalidate();
    }
}
