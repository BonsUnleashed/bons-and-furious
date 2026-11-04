package bons.furious.mixin.fancymenu_files_c49;

import bons.furious.patch.fancymenu_files_c49.ProbeInterval;
import de.keksuccino.fancymenu.util.WebUtils;
import java.time.Duration;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * fancymenu_internet_probe_interval (FancyMenu 3.9.14; client side; trade-off, off by default): the refresh delay WebUtils'
 * static initialiser hands to the new InternetAvailabilityMonitor (20 s) becomes the switch's value in seconds (see
 * ProbeInterval); the probe, its timeouts, the scheduler and the published answer are unchanged.
 */
@Mixin(value = WebUtils.class, remap = false)
public abstract class WebUtilsProbeIntervalMixin {
    @ModifyArg(method = "<clinit>",
            at = @At(value = "INVOKE", target = "Lde/keksuccino/fancymenu/util/InternetAvailabilityMonitor;<init>(Lde/keksuccino/fancymenu/util/InternetAvailabilityProbe;Ljava/util/function/Supplier;Ljava/time/Duration;Ljava/util/function/Consumer;)V"),
            index = 2, require = 1, allow = 1)
    private static Duration bons$fancymenuFilesC49ProbeDelay(Duration stock) {
        return ProbeInterval.delay(stock);
    }
}
