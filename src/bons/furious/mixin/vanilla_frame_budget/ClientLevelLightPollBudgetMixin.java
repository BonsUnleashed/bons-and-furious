package bons.furious.mixin.vanilla_frame_budget;

import bons.furious.patch.vanilla_frame_budget.FrameBudget;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.Deque;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * vanilla_light_poll_budget (Minecraft 1.20.1 on Forge 47.4.28, client; trade-off, off by default): ClientLevel.
 * pollLightUpdates (m_194141_) keeps its own loop (j = n &lt; 1000 ? max(10, n / 10) : n jobs of the n queued). The wrapper
 * of its one lightUpdateQueue.size() call notes the start and the budget (0.5 ms + 0.02 ms x n) while a world is played;
 * the wrapper of its one poll() call answers null - the loop's own "queue empty" end - once a job has run and the budget is
 * used. So never more jobs than stock, at least one per frame, the rest stays queued in its order. Helper:
 * {@link FrameBudget}. require = 0: a mod that rewrote the loop leaves the switch out.
 */
@Mixin(value = ClientLevel.class, remap = false)
public abstract class ClientLevelLightPollBudgetMixin {
    @WrapOperation(method = "m_194141_", require = 0, at = @At(value = "INVOKE", target = "Ljava/util/Deque;size()I"))
    private int bons$lightBudgetStart(Deque<Runnable> queue, Operation<Integer> original) {
        return FrameBudget.lightQueueSize(queue, original);
    }

    @WrapOperation(method = "m_194141_", require = 0, at = @At(value = "INVOKE", target = "Ljava/util/Deque;poll()Ljava/lang/Object;"))
    private Object bons$lightBudgetPoll(Deque<Runnable> queue, Operation<Object> original) {
        return FrameBudget.lightPoll(queue, original);
    }
}
