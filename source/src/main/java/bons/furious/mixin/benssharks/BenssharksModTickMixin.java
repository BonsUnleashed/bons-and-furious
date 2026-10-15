package bons.furious.mixin.benssharks;

import java.util.Collection;
import net.minecraft.util.Tuple;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Ben's Sharks 1.2.6: leave queued work unchanged; avoid the empty queue's temporary list. */
@Mixin(targets = "net.mcreator.sharks.BenssharksMod", remap = false)
public abstract class BenssharksModTickMixin {
    @Shadow(remap = false) @Final
    private static Collection<Tuple<Runnable, Integer>> workQueue;

    @Inject(method = "tick(Lnet/neoforged/neoforge/event/tick/ServerTickEvent$Post;)V",
            at = @At("HEAD"), cancellable = true, remap = false)
    private void bons$sharksIdleQueue(ServerTickEvent.Post event, CallbackInfo ci) {
        if (workQueue.isEmpty()) ci.cancel();
    }
}
