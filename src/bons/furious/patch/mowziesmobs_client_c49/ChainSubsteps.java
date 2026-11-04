package bons.furious.patch.mowziesmobs_client_c49;

import bons.pure.config.PureConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch mowziesmobs_dynamic_chain_substep_cap (Mowzie's Mobs 1.7.3; client; since 1.0.49; a trade-off,
 * off by default; stands aside on 1.8.2, which has no updateChain(FFFFFIZ)V). Idea: Collections Of Optimizations
 * (mowziesmobs.dynamicChainSubstepCap), idea text only. Our own logic; no Mowzie's Mobs code (custom licence, injector
 * only).
 *
 * GeckoDynamicChain.updateChain (the swinging chains of Mowzie's GeckoLib mobs, e.g. the Sculptor's) runs the spring
 * simulation updateSpringConstraint with the number of substeps its caller passes (10 per frame from
 * MowzieGeoEntityRenderer), each substep moving every link.
 *
 * What changes: the substep count passed to updateSpringConstraint is at most the switch's value (default 4). The chains
 * still swing, with a coarser simulation; nothing outside the chain's own render positions reads them. With the runtime
 * flag -Dbons_and_furious.mowziesmobsDynamicChainSubstepCap=false the caller's count is used.
 */
public final class ChainSubsteps {
    public static final String KEY = "mowziesmobs_dynamic_chain_substep_cap";
    public static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.mowziesmobsDynamicChainSubstepCap", "true"));
    /** Most substeps per chain update (read once, on the switch-on path: this class loads from the mixin). */
    public static final int CAP = PureConfig.intValue("mowziesmobs_dynamic_chain_substep_cap", 4, 1, 64);
    private static volatile boolean announced;

    private ChainSubsteps() {
    }

    public static int cap(int substeps) {
        if (!enabled || substeps <= CAP) return substeps;
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: {} applies (Mowzie's Mobs chains simulate at most {} substeps per frame)", KEY, CAP);
        }
        return CAP;
    }
}
