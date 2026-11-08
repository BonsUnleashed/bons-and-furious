package bons.furious.mixin.vanilla_frame_budget;

import bons.furious.patch.vanilla_frame_budget.FrameBudget;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * vanilla_frame_task_budget (Minecraft 1.21.1 on NeoForge 21.1.252, client; trade-off, off by default): two MixinExtras
 * wrappers on Minecraft.
 * <ul>
 * <li>runTick(boolean), its runAllTasks() call (the drain of every queued packet and main-thread task, once per frame
 *     inside {@code if (renderLevel)}): {@link FrameBudget#runAllTasks} drains with pollTask() for at most 0.5 ms + 0.02 ms
 *     per task queued at the start while a world is played; otherwise (switch flag off, no world, a level receiving or
 *     loading screen, an overlay) the call itself.</li>
 * <li>disconnect(Screen, boolean), its dropAllTasks() call (made while a packet listener exists): the tasks this frame's
 *     budgeted drain left (stock would already have run them) run first, then the call itself; from inside a task (a
 *     disconnect packet) the call alone, as in stock.</li>
 * </ul>
 * What differs from stock: tasks can run up to a few frames later, in their order, after that frame's client ticks; that
 * is all. The other drains (clearDownloadedResourcePacks' runAllTasks, managedBlock) are untouched. require = 0: a mod that
 * removed either call leaves that half of the switch out (the other half alone only runs tasks stock would have run).
 * Helper: {@link FrameBudget}; the protected runningTask() comes from {@link ReentrantLoopRunningTaskInvoker}.
 */
@Mixin(value = Minecraft.class, remap = false)
public abstract class MinecraftFrameTaskBudgetMixin {
    @WrapOperation(method = "runTick(Z)V", require = 0, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;runAllTasks()V"))
    private void bons$budgetFrameTasks(Minecraft self, Operation<Void> original) {
        FrameBudget.runAllTasks(self, original);
    }

    @WrapOperation(method = "disconnect(Lnet/minecraft/client/gui/screens/Screen;Z)V", require = 0,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;dropAllTasks()V"))
    private void bons$runDeferredBeforeDrop(Minecraft self, Operation<Void> original) {
        FrameBudget.beforeDropAllTasks(self, original);
    }
}
