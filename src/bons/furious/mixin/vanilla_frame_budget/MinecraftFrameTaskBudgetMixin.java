package bons.furious.mixin.vanilla_frame_budget;

import bons.furious.patch.vanilla_frame_budget.FrameBudget;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * vanilla_frame_task_budget (Minecraft 1.20.1 on Forge 47.4.28, client; trade-off, off by default): two MixinExtras
 * wrappers on Minecraft.
 * <ul>
 * <li>runTick (m_91383_), its runAllTasks() call (m_18699_, the drain of every queued packet and main-thread task):
 *     {@link FrameBudget#runAllTasks} drains with pollTask() for at most 0.5 ms + 0.02 ms per task queued at the start
 *     while a world is played; otherwise (switch flag off, no world, a loading screen or overlay) the call itself.</li>
 * <li>clearLevel(Screen) (m_91320_), its dropAllTasks() call (m_18698_): the tasks this frame's budgeted drain left (stock
 *     would already have run them) run first, then the call itself; from inside a task (a disconnect packet) the call
 *     alone, as in stock.</li>
 * </ul>
 * What differs from stock: tasks can run up to a few frames later, in their order, after that frame's client ticks; that
 * is all. require = 0: a mod that removed either call leaves that half of the switch out (the other half alone only
 * runs tasks stock would have run). Helper: {@link FrameBudget}; the protected runningTask() comes from
 * {@link ReentrantLoopRunningTaskInvoker}.
 */
@Mixin(value = Minecraft.class, remap = false)
public abstract class MinecraftFrameTaskBudgetMixin {
    @WrapOperation(method = "m_91383_", require = 0, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;m_18699_()V"))
    private void bons$budgetFrameTasks(Minecraft self, Operation<Void> original) {
        FrameBudget.runAllTasks(self, original);
    }

    @WrapOperation(method = "m_91320_", require = 0, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;m_18698_()V"))
    private void bons$runDeferredBeforeDrop(Minecraft self, Operation<Void> original) {
        FrameBudget.beforeDropAllTasks(self, original);
    }
}
