package bons.furious.mixin.alltheleaks_c49;

import bons.furious.patch.alltheleaks_c49.AtlLean;
import bons.furious.patch.alltheleaks_c49.ReportTaskView;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.uncandango.alltheleaks.report.ReportManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * alltheleaks_unread_summary_skip (AllTheLeaks 1.1.3 / 1.1.4, MIT, both sides): MixinExtras @WrapOperation on the
 * Runnable.run call in ReportManager$ReportTask.tick (inside its try / finally, so lastTickReported is set exactly as
 * shipped whether the task ran or not); the update_leak_summary run is left out when nothing can read its result before
 * the next one (AtlLean). Also exposes the task's schedule read-only (ReportTaskView).
 */
@Mixin(targets = "dev.uncandango.alltheleaks.report.ReportManager$ReportTask", remap = false)
public abstract class ReportTaskMixin implements ReportTaskView {
    @Shadow
    @Final
    private int tickInterval;
    @Shadow
    @Final
    private String name;
    @Shadow
    private int lastTickReported;
    @Shadow
    private boolean stopped;

    @Override
    public String bons$atlName() {
        return this.name;
    }

    @Override
    public int bons$atlInterval() {
        return this.tickInterval;
    }

    @Override
    public int bons$atlLastRun() {
        return this.lastTickReported;
    }

    @Override
    public boolean bons$atlStopped() {
        return this.stopped;
    }

    @WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Ljava/lang/Runnable;run()V"))
    private void bons$atlRun(Runnable task, Operation<Void> original) {
        AtlLean.runTask(this, task, AtlLean.tasks(), ReportManager.getCurrentTick(), original);
    }
}
