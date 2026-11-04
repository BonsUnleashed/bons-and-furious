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
 * vanilla_system_report_hardware_reuse (Minecraft 1.20.1, SRG client jar 1.20.1-20230612.114412, not patched by Forge
 * 47.4.28; both sides; exact variant). MixinExtras @WrapOperation on the ignoreErrors (m_143516_) calls of putHardware
 * (m_143535_: "processor", "graphics", "memory") and putMemory (m_143541_: "physical memory", "virtual memory"):
 * HardwareLines keeps the lines of the first successful "processor", "graphics" and "physical memory" group and writes
 * them again for later reports instead of running the group; every other group runs as before. Oculus' and Patchouli's
 * injections into the constructor are untouched. Only our own logic is carried. See HardwareLines.
 */
@Mixin(value = SystemReport.class, remap = false)
public abstract class SystemReportHardwareMixin {
    @Shadow
    @Final
    private Map<String, String> f_143512_;

    @WrapOperation(method = "m_143535_", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/SystemReport;m_143516_(Ljava/lang/String;Ljava/lang/Runnable;)V"))
    private void bons$hardwareGroup(SystemReport report, String group, Runnable body, Operation<Void> original) {
        HardwareLines.group(report, this.f_143512_, group, body, original);
    }

    @WrapOperation(method = "m_143541_", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/SystemReport;m_143516_(Ljava/lang/String;Ljava/lang/Runnable;)V"))
    private void bons$memoryGroup(SystemReport report, String group, Runnable body, Operation<Void> original) {
        HardwareLines.group(report, this.f_143512_, group, body, original);
    }
}
