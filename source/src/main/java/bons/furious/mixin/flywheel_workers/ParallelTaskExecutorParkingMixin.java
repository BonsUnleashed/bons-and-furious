package bons.furious.mixin.flywheel_workers;

import bons.furious.patch.flywheel_workers.FlywheelParking;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.engine_room.flywheel.impl.task.ParallelTaskExecutor;
import dev.engine_room.flywheel.impl.task.ThreadGroupNotifier;
import java.util.Deque;
import java.util.concurrent.atomic.AtomicBoolean;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * flywheel_worker_parking (Flywheel 1.0.6 in Create 6.0.10 for NeoForge 1.21.1, client; exact variant), part 1 of 2:
 * ParallelTaskExecutor.
 * <ul>
 * <li>a per-executor {@link FlywheelParking.Idle} (worker slots), created in the constructor;</li>
 * <li>execute: its taskNotifier.postNotification() call (the synchronized notifyAll after the task was queued) unparks one
 *     armed worker instead (FlywheelParking.notifyWorkers; the stock call while workers wait on the monitor);</li>
 * <li>stopWorkers: its taskNotifier.notifyAll() call is made, then every armed worker is unparked; at RETURN, after the
 *     joins, the slots are freed.</li>
 * </ul>
 * The executor offers its queue, running flag, thread count and slots to the helper through {@link FlywheelParking.Host}.
 * Flywheel is MIT; no Flywheel code is carried (Flywheel's own calls run where the helper keeps them). require = 0 on every
 * injector: a Flywheel build without these calls leaves the switch out of that method (the guard stops it before that).
 */
@Mixin(value = ParallelTaskExecutor.class, remap = false)
public abstract class ParallelTaskExecutorParkingMixin implements FlywheelParking.Host {
    @Shadow @Final private Deque<Runnable> taskQueue;
    @Shadow @Final private AtomicBoolean running;
    @Shadow @Final private int threadCount;

    @Unique
    private final FlywheelParking.Idle bons$parking = new FlywheelParking.Idle();

    @Override
    public FlywheelParking.Idle bons$idle() {
        return bons$parking;
    }

    @Override
    public Deque<Runnable> bons$taskQueue() {
        return taskQueue;
    }

    @Override
    public AtomicBoolean bons$running() {
        return running;
    }

    @Override
    public int bons$threadCount() {
        return threadCount;
    }

    @WrapOperation(method = "execute", require = 0, at = @At(value = "INVOKE",
            target = "Ldev/engine_room/flywheel/impl/task/ThreadGroupNotifier;postNotification()V"))
    private void bons$wakeOneWorker(ThreadGroupNotifier notifier, Operation<Void> original) {
        FlywheelParking.notifyWorkers(this, notifier, original);
    }

    @WrapOperation(method = "stopWorkers", require = 0, at = @At(value = "INVOKE", target = "Ljava/lang/Object;notifyAll()V"))
    private void bons$wakeAllWorkers(Object notifier, Operation<Void> original) {
        FlywheelParking.stopNotify(this, notifier, original);
    }

    @Inject(method = "stopWorkers", require = 0, at = @At("RETURN"))
    private void bons$freeSlots(CallbackInfo ci) {
        FlywheelParking.afterStop(this);
    }
}
