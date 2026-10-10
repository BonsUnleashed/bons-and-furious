package bons.furious.patch.cataclysm_client;

import com.github.alexthe666.citadel.client.event.EventPosePlayerHand;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.TriState;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Bons and Furious switch citadel_pose_hand_events (Citadel, 1.21.1 tested build citadel-2.7.1-1.21.1; client, since
 * 1.0.36). Ours; no Citadel code.
 *
 * Citadel's HumanoidModelMixin.citadel_poseRightArm / citadel_poseLeftArm do what Cataclysm's handlers do with Citadel's own
 * EventPosePlayerHand: build it, post it on NeoForge.EVENT_BUS, cancel the vanilla arm pose when a listener set
 * TriState.TRUE. When no registered listener can act for this entity, posting changes nothing (no write, the result stays
 * DEFAULT, the handler does not cancel), so the handler returns before building the event. Anything else posts as before.
 *
 * Ported to 1.21.1: Citadel 2.7.1's handlers and event are unchanged in shape (the result is NeoForge's TriState instead of
 * Forge's Event.Result). The two listeners 1.0.36 recognised and mirrored, Alex's Caves' and Alex's Mobs' onPoseHand, have
 * no NeoForge 1.21.1 build (no author build on Modrinth or CurseForge; none of the 444 1.21.1 jars of this project
 * references Citadel's event besides Citadel's own mixin), so no listener is known here: the switch acts only while
 * Citadel's event has no listener at all, which is the same check with an empty list of known listeners. Any listener
 * (whatever it does) makes the handler post as before.
 *
 * -Dbons_and_furious.citadelPoseHandEvents=false always posts; .shadow=true (verification runs) builds and posts the event
 * itself whenever it would skip (cancelling exactly as Citadel's handler does) and counts any post that changed the pose
 * or set a result.
 */
public final class CitadelPoseHand {
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.citadelPoseHandEvents", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.citadelPoseHandEvents.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    /** Known listeners of Citadel's event on 1.21.1: none (see the class comment). */
    private static final int KNOWN_LISTENERS = 0;
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
                watch = new PoseHandEvents.Watch("citadel_pose_hand_events", PoseHandEvents.listenerList(EventPosePlayerHand.class), KNOWN_LISTENERS);
            } catch (Throwable t) {
                failed = true;
                PoseHandEvents.LOGGER.warn("Bons and Furious: citadel_pose_hand_events stands down ({}); Citadel posts its arm-pose event as before", t.toString());
            }
            return watch;
        }
    }

    /** As CataclysmPoseHand.skip, for Citadel's handler: true only while Citadel's event has no listener. */
    public static boolean skip(LivingEntity entity, HumanoidModel<?> model, boolean left, CallbackInfo theirCi) {
        if (!enabled) return false;
        PoseHandEvents.Watch w = watch();
        if (w == null) return false;
        if (w.kinds() != 0) return false;
        if (!announced) {
            announced = true;
            PoseHandEvents.LOGGER.info("Bons and Furious: citadel_pose_hand_events applies (Citadel's arm-pose event is built and posted only when a listener is registered){}",
                    SHADOW ? " - shadow verification on" : "");
        }
        if (SHADOW) {
            int[] before = PoseHandEvents.snapshot(model);
            EventPosePlayerHand event = new EventPosePlayerHand(entity, model, left);
            NeoForge.EVENT_BUS.post(event);
            SHADOW_CHECKS.incrementAndGet();
            if (event.getResult() == TriState.TRUE) theirCi.cancel();
            if (event.getResult() != TriState.DEFAULT || !java.util.Arrays.equals(before, PoseHandEvents.snapshot(model)))
                PoseHandEvents.mismatch("citadel_pose_hand_events", SHADOW_MISMATCHES, entity + " result " + event.getResult());
        }
        return true;
    }
}
