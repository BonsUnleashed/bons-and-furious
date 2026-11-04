package bons.furious.patch.mowziesmobs182_client_c49;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joml.Matrix4f;

/**
 * Bons and Furious switch mowziesmobs_chain_rotation_hoist (Mowzie's Mobs 1.8.2; client; since 1.0.49; stands down on
 * 1.7.3, which has another GeckoDynamicChain.setChainFromRenderPos). Idea: Collections Of Optimizations
 * (mowziesmobs.hoistChainRenderMatrix), idea text only. Our own logic; no Mowzie's Mobs code (custom licence, injectors only).
 *
 * GeckoDynamicChain.setChainFromRenderPos (the swinging chains of Mowzie's GeckoLib mobs: the Elokosa's tail, the Sculptor's
 * chains) runs over the chain's bones and for every bone but the last asks the renderer for its model render matrix
 * (MowzieGeoEntityRenderer.getModelRenderMatrix: a new matrix holding the inverse of the renderer's entity translations
 * times its model translations) only to take its rotation. The renderer's two matrices do not change inside that loop.
 *
 * What changes: within one call, the first bone's matrix is worked out as before and kept; every later bone gets a new
 * matrix holding the same values (and JOML properties) instead of another inversion and product. Every bone ends with the
 * same world matrix as before.
 *
 * -Dbons_and_furious.mowziesmobsChainRotationHoist=false works the matrix out for every bone.
 */
public final class ChainHoist {
    public static final String KEY = "mowziesmobs_chain_rotation_hoist";
    public static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.mowziesmobsChainRotationHoist", "true"));
    private static volatile boolean announced;

    private ChainHoist() {
    }

    /** The wrapped MowzieGeoEntityRenderer.getModelRenderMatrix() of setChainFromRenderPos (one renderer per call). */
    public static Matrix4f renderMatrix(Object renderer, Operation<Matrix4f> original, LocalRef<Matrix4f> kept) {
        if (!enabled) return original.call(renderer);
        Matrix4f first = kept.get();
        if (first == null) {
            Matrix4f m = original.call(renderer);
            kept.set(new Matrix4f(m));
            return m;
        }
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: {} applies (Mowzie's Mobs chains work their render matrix out once per update)", KEY);
        }
        return new Matrix4f(first);
    }
}
