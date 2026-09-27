package agentcraft.pure;

import bons.pure.config.PureConfig;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;

/** Mod entry point. The optimizations themselves are applied by coremod scripts and mixins at class load. */
@Mod("bons_pure_optimizations")
public final class PureOptimizations {
    public PureOptimizations() {
        PureConfig.load();
        long disabled = PureConfig.snapshot().values().stream().filter(v -> !v).count();
        LogManager.getLogger("Bons Pure Optimizations").info("Bons Pure Optimizations {} constructed; {} switch(es) disabled in {}",
                PureConfig.version(), disabled, PureConfig.path());
    }
}
