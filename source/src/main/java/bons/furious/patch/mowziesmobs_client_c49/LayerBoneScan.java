package bons.furious.patch.mowziesmobs_client_c49;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import software.bernie.geckolib.cache.object.GeoBone;

/**
 * Bons and Furious switch mowziesmobs_umvuthana_layer_bone_test (Mowzie's Mobs; 1.21.1 tested build: Mowzie's Mobs
 * 1.21.1-1.8.2 with GeckoLib 4.9.3; client; since 1.0.49). Idea: Collections Of Optimizations (mowziesmobs.leanLayerBoneScan),
 * idea text only. Our own logic; no Mowzie's Mobs code (custom licence, injectors only).
 *
 * GeckoLib calls a render layer's renderForBone for every bone of the model each frame. UmvuthiSunLayer pushes a pose,
 * translates it to the bone's pivot, draws only for its sun bone ("sun_render"), asks the buffer source for the render
 * type, and pops the pose. (In this build the Umvuthana mask layer and the Umvuthana sun layer draw once per frame from
 * their bone's tracked pose and have no per-bone pass.)
 *
 * What changes: for a bone the layer does not draw on, the pushPose, translateToPivotPoint and popPose calls are left out;
 * the super call, the layer's own conditions, its name test and the bufferSource.getBuffer call (which can end another
 * batch) still run. Why it is identical: between that push and pop nothing reads the pose stack (the name test fails, no
 * drawing), and a push, a translate and a pop leave the stack exactly as it was. A bone without a name runs stock.
 *
 * -Dbons_and_furious.mowziesmobsUmvuthanaLayerBoneTest=false runs every call stock.
 */
public final class LayerBoneScan {
    public static final String KEY = "mowziesmobs_umvuthana_layer_bone_test";
    public static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.mowziesmobsUmvuthanaLayerBoneTest", "true"));
    private static volatile boolean announced;

    private LayerBoneScan() {
    }

    /** True when the layer will not draw on this bone (its name is none of the layer's bone names). */
    public static boolean skip(GeoBone bone, String drawn, String alsoDrawn) {
        if (!enabled) return false;
        String name = bone.getName();
        if (name == null || name.equals(drawn) || alsoDrawn != null && name.equals(alsoDrawn)) return false;
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: {} applies (the Umvuthi sun layer leaves out the pose work on bones it does not draw)", KEY);
        }
        return true;
    }
}
