package bons.furious.patch.cataclysm_client;

import com.github.alexthe666.citadel.client.event.EventPosePlayerHand;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.Event;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Bons and Furious switch citadel_pose_hand_events (Citadel 2.6.1 with Alex's Caves 2.0.2 and Alex's Mobs 1.22.9, client,
 * since 1.0.36). Ours; no Citadel, Alex's Caves or Alex's Mobs code.
 *
 * Citadel's HumanoidModelMixin.citadel_poseRightArm / citadel_poseLeftArm do what Cataclysm's handlers do with Citadel's own
 * EventPosePlayerHand. Its listeners in this pack: Alex's Caves' ClientEvents.onPoseHand (acts only for the items, vehicles
 * and effect AlexsCavesPoseHand lists) and Alex's Mobs' ClientEvents.onPoseHand (acts only while the entity uses a Vine
 * Lasso: every write and setResult needs isUsingItem()). While every listener is one of these two (PoseHandEvents.Watch) and
 * none can act for this entity, posting changes nothing and the handler returns before building the event. Anything else
 * posts as before. Measured where it came from: Alex's Caves' listener alone was 0.16% self of the render thread
 * (an earlier profile of this pack, entities window).
 *
 * -Dbons_and_furious.citadelPoseHandEvents=false always posts; .shadow=true (verification runs) builds and posts the event
 * itself whenever it would skip (cancelling exactly as Citadel's handler does) and counts any post that changed the pose
 * or set a result.
 */
public final class CitadelPoseHand {
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.citadelPoseHandEvents", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.citadelPoseHandEvents.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    private static volatile PoseHandEvents.Watch watch;
    private static volatile boolean failed, announced;

    private CitadelPoseHand() {
    }

    private static PoseHandEvents.Watch watch() {
        PoseHandEvents.Watch w = watch;
        if (w != null || failed) return w;
        synchronized (CitadelPoseHand.class) {
            if (watch != null || failed) return watch;
            try {
                watch = new PoseHandEvents.Watch("citadel_pose_hand_events", new EventPosePlayerHand(null, null, false).getListenerList(),
                        PoseHandEvents.busId(), PoseHandEvents.ALEXSCAVES_CLIENT_EVENTS | PoseHandEvents.ALEXSMOBS_CLIENT_EVENTS);
            } catch (Throwable t) {
                failed = true;
                PoseHandEvents.LOGGER.warn("Bons and Furious: citadel_pose_hand_events stands down ({}); Citadel posts its arm-pose event as before", t.toString());
            }
            return watch;
        }
    }

    /** As CataclysmPoseHand.skip, for Citadel's handler and listeners. */
    public static boolean skip(LivingEntity entity, HumanoidModel<?> model, boolean left, CallbackInfo theirCi) {
        if (!enabled) return false;
        PoseHandEvents.Watch w = watch();
        if (w == null) return false;
        int kinds = w.kinds();
        if (kinds < 0) return false;
        if ((kinds & PoseHandEvents.ALEXSMOBS_CLIENT_EVENTS) != 0 && entity.m_6117_()) return false;
        if ((kinds & PoseHandEvents.ALEXSCAVES_CLIENT_EVENTS) != 0 && AlexsCavesPoseHand.mayAct(entity)) return false;
        if (!announced) {
            announced = true;
            PoseHandEvents.LOGGER.info("Bons and Furious: citadel_pose_hand_events applies (Citadel's arm-pose event is built and posted only when a listener can act){}",
                    SHADOW ? " - shadow verification on" : "");
        }
        if (SHADOW) {
            int[] before = PoseHandEvents.snapshot(model);
            EventPosePlayerHand event = new EventPosePlayerHand(entity, model, left);
            MinecraftForge.EVENT_BUS.post(event);
            SHADOW_CHECKS.incrementAndGet();
            if (event.getResult() == Event.Result.ALLOW) theirCi.cancel();
            if (event.getResult() != Event.Result.DEFAULT || !java.util.Arrays.equals(before, PoseHandEvents.snapshot(model)))
                PoseHandEvents.mismatch("citadel_pose_hand_events", SHADOW_MISMATCHES, entity + " result " + event.getResult());
        }
        return true;
    }
}
