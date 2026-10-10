package bons.furious.patch.cataclysm_client;

import com.github.L_Ender.lionfishapi.server.animation.IAnimatedEntity;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch cataclysm_idle_animate_skip (L_Ender's Cataclysm with Lionfish API, 1.21.1 tested builds
 * L_Ender's Cataclysm 1.21.1-3.33 and lionfishapi-3.1; client, since 1.0.36). Ours; no Cataclysm code (CC-BY-NC-ND-4.0).
 *
 * The animate methods of Ignis_Model, The_Leviathan_Model, Ender_Guardian_Model and Amethyst_Crab_Model (56,495 / 38,298 /
 * 14,692 / 14,464 bytes of bytecode, over HotSpot's 8,000-byte limit, so they always run in the interpreter) start with
 * resetToDefaultPose() and animator.update(entity), then make 1,497 to 6,689 calls per frame: ModelAnimator.setAnimation /
 * startKeyframe / setStaticKeyframe / resetKeyframe / rotate / move / endKeyframe, Math.toRadians, Ignis' synced-data
 * getters and Ignis' poke/bodycheck helpers (which make the same kinds of calls). Read from the bytecode after the update
 * call: no field or array write, no allocation, no other call; all 84 setAnimation arguments are static final animation
 * constants of the four entity classes made by Animation.create (never IAnimatedEntity.NO_ANIMATION, itself
 * Animation.create(0)).
 *
 * update() sets correctAnimation = false and tempTick = prevTempTick = 0 and clears both transform maps. While the
 * entity's animation is NO_ANIMATION every setAnimation(X) sets tempTick = prevTempTick = 0 and correctAnimation =
 * (NO_ANIMATION == X) = false, and every other ModelAnimator call starts with if (correctAnimation): nothing else changes,
 * and the animator ends exactly as update() left it. So the rest of the method is skipped right after update() when
 * entity.getAnimation() == NO_ANIMATION (a field read, LLibrary_Monster.getAnimation); setupAnim's idle movement after
 * animate() is untouched.
 *
 * Ported to 1.21.1: the four animate methods, poke and bodycheck are instruction-for-instruction the 3.16 ones (Amethyst
 * Crab: one ldc_w encoded as ldc), Lionfish 3.1's ModelAnimator, Animation and IAnimatedEntity are 2.8's (endKeyframe reads
 * the partial tick through the 1.21.1 DeltaTracker; the correctAnimation gates are unchanged). Cataclysm 3.33 no longer
 * has the old Ancient_Remnant_Model (the Ancient Remnant now uses Ancient_Remnant_Rework_Model, a different animation
 * system), so its mixin is not ported.
 *
 * -Dbons_and_furious.cataclysmIdleAnimateSkip=false runs the whole method every time.
 */
public final class IdleAnimateSkip {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.cataclysmIdleAnimateSkip", "true"));
    private static volatile boolean announced;

    private IdleAnimateSkip() {
    }

    /** True when the rest of animate() (after animator.update) can only leave the animator and the model as they are. */
    public static boolean idle(IAnimatedEntity entity) {
        if (!enabled || entity.getAnimation() != IAnimatedEntity.NO_ANIMATION) return false;
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: cataclysm_idle_animate_skip applies (boss keyframe passes are skipped while no attack animation plays)");
        }
        return true;
    }
}
