package bons.furious.mixin.benssharks;

import java.util.AbstractMap;
import java.util.Collection;

import net.minecraftforge.event.TickEvent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * benssharks_work_queue_idle (Ben's Sharks 1.3.0): MCreator's server work queue: every server tick (both phases) the handler
 * ran, and at END it built a new ArrayList, walked the queue and removeAll'd - even with nothing queued. Nothing queued:
 * nothing to do, so the handler returns at once; with work queued it runs as shipped.
 */
@Mixin(targets = "net.mcreator.sharks.BenssharksMod", remap = false)
public abstract class BenssharksModTickMixin {
    @Shadow(remap = false)
    @Final
    private static Collection<AbstractMap.SimpleEntry<Runnable, Integer>> workQueue;

    @Inject(method = "tick(Lnet/minecraftforge/event/TickEvent$ServerTickEvent;)V", at = @At("HEAD"), cancellable = true, remap = false)
    private void bons$sharksIdleQueue(TickEvent.ServerTickEvent event, CallbackInfo ci) {
        if (workQueue.isEmpty()) ci.cancel();
    }
}
