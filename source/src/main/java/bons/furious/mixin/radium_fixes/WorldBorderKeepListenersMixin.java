package bons.furious.mixin.radium_fixes;

import bons.furious.patch.radium_fixes.WorldBorderKeepListeners;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.WeakHashMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * radium_world_border_keep_listeners (Radium, LGPL-3.0; 1.21.1 tested build radium-mc1.21.1-0.13.1+git.4994e83; both
 * sides). Fix.
 *
 * Target: Radium's own class WorldBorderListenerOnceMulti (common/world/listeners), the per-border set of block-entity
 * tickers that cache "inside the world border". Its four handlers for border events that do not move the border (warning
 * time, warning distance, damage per block, damage buffer) end with WeakHashMap.clear() like the shape-change handlers,
 * although the tickers ignore those events: they lose the subscription and keep a cached answer that the next shape
 * change no longer resets. This wrap skips only that clear() call in only those four handlers; the loop that calls every
 * subscriber's handler before it is unchanged, and so are the shape-change handlers. The tickers then reset exactly as
 * Radium intends, and each cached answer equals vanilla's WorldBorder.isWithinBounds again (see WorldBorderKeepListeners).
 * Radium is LGPL-3.0; no Radium code is carried, only the wrap around its call.
 *
 * Ported to 1.21.1: nothing in the wrap changed (Radium 0.13.1 already uses the Mojang handler names); the tickers' reset
 * callback is now WorldBorderListenerOnce.lithium$onWorldBorderShapeChange (guard updated).
 */
@Mixin(targets = "me.jellysquid.mods.lithium.common.world.listeners.WorldBorderListenerOnceMulti", remap = false)
public abstract class WorldBorderKeepListenersMixin {
    @WrapOperation(method = {
            "onBorderSetWarningTime(Lnet/minecraft/world/level/border/WorldBorder;I)V",
            "onBorderSetWarningBlocks(Lnet/minecraft/world/level/border/WorldBorder;I)V",
            "onBorderSetDamagePerBlock(Lnet/minecraft/world/level/border/WorldBorder;D)V",
            "onBorderSetDamageSafeZOne(Lnet/minecraft/world/level/border/WorldBorder;D)V"},
            at = @At(value = "INVOKE", target = "Ljava/util/WeakHashMap;clear()V"), require = 4, allow = 4)
    private void bons$keepSubscribersOnSizeUnrelatedEvent(WeakHashMap<?, ?> subscribers, Operation<Void> original) {
        if (!WorldBorderKeepListeners.enabled) {
            original.call(subscribers);
            return;
        }
        WorldBorderKeepListeners.kept(subscribers.size());
    }
}
