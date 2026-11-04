package bons.furious.mixin.alltheleaks_c49;

import bons.furious.patch.alltheleaks_c49.AtlLean;
import dev.uncandango.alltheleaks.report.ReportManager;
import java.util.Map;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * alltheleaks_unread_summary_skip (AllTheLeaks 1.1.3 / 1.1.4): read access to ReportManager's static task map (to find
 * the passive_memory_leak_report task's schedule). The map is a static final field set once in the class initializer, so
 * its reference is handed to the helper at the end of that initializer. Adds nothing to the map, changes nothing.
 */
@Mixin(value = ReportManager.class, remap = false)
public abstract class ReportManagerTasksMixin {
    @SuppressWarnings("rawtypes")
    @Shadow
    @Final
    static Map tasks;

    @SuppressWarnings("unchecked")
    @Inject(method = "<clinit>", at = @At("RETURN"))
    private static void bons$atlTasks(CallbackInfo ci) {
        AtlLean.captureTasks(tasks);
    }
}
