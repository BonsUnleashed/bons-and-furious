package bons.furious.mixin.flywheel_workers;

import bons.furious.patch.flywheel_workers.FlywheelParking;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.engine_room.flywheel.impl.task.ParallelTaskExecutor;
import dev.engine_room.flywheel.impl.task.ThreadGroupNotifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * flywheel_worker_parking (Flywheel 1.0.5 in Create 6.0.8, client; exact variant), part 2 of 2: ParallelTaskExecutor's
 * private inner WorkerThread. spinThenWait's taskNotifier.awaitNotification() call (the synchronized wait() after the
 * 10 us spin) becomes FlywheelParking.park: arm this worker's slot, look at the queue and the running flag once more,
 * park (100 ms safety timeout), disarm; with the flag off the call itself. The worker keeps its slot index (handed out once
 * by the executor's Idle) in a field of its own. The outer executor comes from the synthetic this$0 field. require = 0.
 */
@Mixin(targets = "dev.engine_room.flywheel.impl.task.ParallelTaskExecutor$WorkerThread", remap = false)
public abstract class WorkerThreadParkingMixin {
    @Shadow @Final ParallelTaskExecutor this$0;

    /** This worker's slot index + 1 (0 = none handed out yet). */
    @Unique
    private int bons$slotPlusOne;

    @WrapOperation(method = "spinThenWait", require = 0, at = @At(value = "INVOKE",
            target = "Ldev/engine_room/flywheel/impl/task/ThreadGroupNotifier;awaitNotification()V"))
    private void bons$park(ThreadGroupNotifier notifier, Operation<Void> original) {
        FlywheelParking.Host host = (FlywheelParking.Host) (Object) this$0;
        int slot = bons$slotPlusOne - 1;
        if (slot < 0 && FlywheelParking.enabled) {
            slot = host.bons$idle().assignSlot();
            bons$slotPlusOne = slot + 1;
        }
        FlywheelParking.park(host, slot, notifier, original);
    }
}
