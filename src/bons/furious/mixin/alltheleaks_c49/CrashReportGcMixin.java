package bons.furious.mixin.alltheleaks_c49;

import bons.furious.patch.alltheleaks_c49.AtlLean;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.uncandango.alltheleaks.events.common.ModEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * alltheleaks_crash_report_gc_skip (AllTheLeaks 1.1.3 / 1.1.4, MIT, both sides; a trade-off, off by default): MixinExtras
 * @WrapOperation on the MemoryMonitor.runExplicitGc() call in ModEvent.generateReportForCrashReport (its result is
 * discarded there): not called while the switch is on (AtlLean.crashReportGc). Everything else of the report section runs.
 */
@Mixin(value = ModEvent.class, remap = false)
public abstract class CrashReportGcMixin {
    @WrapOperation(method = "generateReportForCrashReport", at = @At(value = "INVOKE",
            target = "Ldev/uncandango/alltheleaks/feature/common/mods/minecraft/MemoryMonitor;runExplicitGc()Z"))
    private static boolean bons$atlCrashGc(Operation<Boolean> original) {
        return AtlLean.crashReportGc(original);
    }
}
