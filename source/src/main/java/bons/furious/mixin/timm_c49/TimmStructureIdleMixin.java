package bons.furious.mixin.timm_c49;

import bons.furious.patch.timm_c49.TimmStructureIdle;
import com.bawnorton.mixinsquared.TargetHandler;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * timm_structure_scan_idle_skip (The Immersive Music Mod 1.1 for NeoForge 1.21, server side incl. the integrated server,
 * since 1.0.49; kind exact variant). MixinSquared @TargetHandler + cancellable @Inject in TIMM's ServerPlayerMixin.onTick
 * handler (merged at the HEAD of ServerPlayer.doTick), right before its ServerLevel.structureManager() call, i.e. after
 * TIMM's creative and once-a-second tests: while TIMM's structure playlist map is empty the handler returns there (see
 * TimmStructureIdle). Priority 1500: applied after TIMM's mixin (1000). require = 0 / expect = 0: without TIMM's handler
 * there is nothing to change. TIMM is GPL-3.0; no TIMM code is carried.
 */
@Mixin(value = ServerPlayer.class, priority = 1500, remap = false)
public abstract class TimmStructureIdleMixin {
    @TargetHandler(mixin = "com.github.charlyb01.timm.mixin.ServerPlayerMixin", name = "onTick")
    @Inject(method = "@MixinSquared:Handler", cancellable = true, require = 0, expect = 0,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;structureManager()Lnet/minecraft/world/level/StructureManager;"))
    private void bons$timmC49StructureIdle(CallbackInfo theirs, CallbackInfo ci) {
        if (TimmStructureIdle.skip()) ci.cancel();
    }
}
