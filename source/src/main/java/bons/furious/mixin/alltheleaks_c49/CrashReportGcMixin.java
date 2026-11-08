package bons.furious.mixin.alltheleaks_c49;

import bons.furious.patch.alltheleaks_c49.AtlLean;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * alltheleaks_crash_report_gc_skip (AllTheLeaks 1.1.14 for 1.21.1, MIT; client mixin, so the client and single player; a
 * trade-off, off by default): MixinExtras @WrapOperation on the MemoryMonitor.runExplicitGc() call in
 * CommonEvents.generateReportForCrashReport (its result is discarded there): not called while the switch is on
 * (AtlLean.crashReportGc). Everything else of the report section runs. CommonEvents also names client classes, so a
 * dedicated server does not get this mixin.
 */
@Mixin(targets = "dev.uncandango.alltheleaks.events.CommonEvents", remap = false)
public abstract class CrashReportGcMixin {
    @WrapOperation(method = "generateReportForCrashReport()Ljava/lang/String;", at = @At(value = "INVOKE",
            target = "Ldev/uncandango/alltheleaks/feature/common/mods/minecraft/MemoryMonitor;runExplicitGc()Z"))
    private static boolean bons$atlCrashGc(Operation<Boolean> original) {
        return AtlLean.crashReportGc(original);
    }
}
