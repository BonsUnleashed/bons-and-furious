package bons.furious.mixin.vanilla_report_reuse;

import bons.furious.patch.vanilla_report_reuse.HardwareLines;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.Map;
import net.minecraft.SystemReport;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * vanilla_system_report_hardware_reuse (Minecraft 1.21.1 with NeoForge 21.1.252, not patched by NeoForge; both sides; exact
 * variant). MixinExtras @WrapOperation on the ignoreErrors calls of putHardware ("processor", "graphics", "memory",
 * "storage") and putMemory ("physical memory", "virtual memory"): HardwareLines keeps the lines of the first successful
 * "processor", "graphics" and "physical memory" group and writes them again for later reports instead of running the
 * group; every other group (the storage space lines included) runs as before. The constructor's own groups and lines are
 * not touched, so Patchouli's injection at its return (1.21.1-93) and Create's accessors (6.0.10) see the report as
 * before. Only our own logic is carried. See HardwareLines.
 */
@Mixin(value = SystemReport.class, remap = false)
public abstract class SystemReportHardwareMixin {
    @Shadow
    @Final
    private Map<String, String> entries;

    @WrapOperation(method = "putHardware(Loshi/SystemInfo;)V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/SystemReport;ignoreErrors(Ljava/lang/String;Ljava/lang/Runnable;)V"))
    private void bons$hardwareGroup(SystemReport report, String group, Runnable body, Operation<Void> original) {
        HardwareLines.group(report, this.entries, group, body, original);
    }

    @WrapOperation(method = "putMemory(Loshi/hardware/GlobalMemory;)V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/SystemReport;ignoreErrors(Ljava/lang/String;Ljava/lang/Runnable;)V"))
    private void bons$memoryGroup(SystemReport report, String group, Runnable body, Operation<Void> original) {
        HardwareLines.group(report, this.entries, group, body, original);
    }
}
