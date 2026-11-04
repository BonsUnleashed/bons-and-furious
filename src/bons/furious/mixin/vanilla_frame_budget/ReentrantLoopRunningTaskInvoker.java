package bons.furious.mixin.vanilla_frame_budget;

import bons.furious.patch.vanilla_frame_budget.FrameBudget;
import net.minecraft.util.thread.ReentrantBlockableEventLoop;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * vanilla_frame_task_budget (Minecraft 1.20.1, client), part 2 of 2: an invoker for ReentrantBlockableEventLoop's
 * protected runningTask() (m_18767_: a task is running on this loop right now). FrameBudget asks it before it runs the
 * frame's deferred tasks at clearLevel: inside a task (a disconnect packet) stock drops the queued tasks, so they are not
 * run. Adds no behaviour and carries no Minecraft code.
 */
@Mixin(value = ReentrantBlockableEventLoop.class, remap = false)
public interface ReentrantLoopRunningTaskInvoker extends FrameBudget.RunningTaskProbe {
    @Override
    @Invoker("m_18767_")
    boolean bons$runningTask();
}
