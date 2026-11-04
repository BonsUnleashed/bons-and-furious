package bons.furious.mixin.morerelics_coo49;

import bons.furious.patch.morerelics_coo49.MrTasks;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.Iterator;
import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * morerelics_delayed_task_safety (More Relics 1.7.7, and 1.7.6 whose DelayedTaskManager has the same bytes; All Rights
 * Reserved: no code of the mod is carried; server side; since 1.0.49). Fix.
 *  - DelayedTaskManager.onServerEvent walks its static task list with the list's own iterator while it runs the tasks, so
 *    a task that schedules another task (addTask appends to the same list) ends the walk with a
 *    ConcurrentModificationException in the server tick. MixinExtras @WrapOperation on that first List.iterator() call:
 *    MrTasks hands out an iterator over the entries the list has when the pass starts, read by index (the same tasks in
 *    the same order, nothing copied; a task added during the pass waits for the next pass, as one added from anywhere
 *    else does).
 *  - @Inject at RETURN of DelayedTaskManager's static initialiser: MrTasks registers a ServerStoppedEvent listener that
 *    empties the list (tasks still pending when a world closes would otherwise keep that world's player and level objects
 *    alive and run into the next world) and resets TwinFangs.inDualStrike, as a fresh game has it.
 */
@Mixin(targets = "com.blorb.morerelics.helper.DelayedTaskManager", remap = false)
public abstract class MrDelayedTasksMixin {
    @SuppressWarnings({"rawtypes"})
    @WrapOperation(method = "onServerEvent(Lnet/minecraftforge/event/TickEvent$ServerTickEvent;)V",
            at = @At(value = "INVOKE", target = "Ljava/util/List;iterator()Ljava/util/Iterator;", ordinal = 0, remap = false),
            require = 1, allow = 1, remap = false)
    private static Iterator bons$morerelicsCoo49TaskPass(List tasks, Operation<Iterator> original) {
        return MrTasks.passIterator(tasks, original);
    }

    @Inject(method = "<clinit>", at = @At("RETURN"), remap = false, require = 1, allow = 1)
    private static void bons$morerelicsCoo49InstallStop(CallbackInfo ci) {
        MrTasks.install();
    }
}
