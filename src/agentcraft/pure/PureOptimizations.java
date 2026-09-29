package agentcraft.pure;

import bons.pure.config.PureConfig;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;

/** Mod entry point. The optimizations themselves are applied by coremod scripts and mixins at class load. */
@Mod("bons_and_furious")
public final class PureOptimizations {
    public PureOptimizations() {
        PureConfig.load();
        long disabled = PureConfig.snapshot().values().stream().filter(v -> !v).count();
        LogManager.getLogger("Bons and Furious").info("Bons and Furious {} constructed; {} switch(es) disabled in {}",
                PureConfig.version(), disabled, PureConfig.path());
    }
}
