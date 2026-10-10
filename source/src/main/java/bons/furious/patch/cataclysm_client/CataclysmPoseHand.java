package bons.furious.patch.cataclysm_client;

import com.github.L_Ender.cataclysm.client.event.EventPosePlayerHand;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.common.NeoForge;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Bons and Furious switch cataclysm_pose_hand_events (L_Ender's Cataclysm, 1.21.1 tested build L_Ender's Cataclysm
 * 1.21.1-3.33; client, since 1.0.36). Ours; no Cataclysm code (CC-BY-NC-ND-4.0).
 *
 * Cataclysm's HumanoidModelMixin.custom_poseRightArm / custom_poseLeftArm (merged at the head of HumanoidModel.poseRightArm
 * / poseLeftArm) build a com.github.L_Ender.cataclysm.client.event.EventPosePlayerHand, post it on NeoForge.EVENT_BUS and
 * cancel the vanilla arm pose when the result is ALLOW. Its only listener is Cataclysm's own ClientEvent.onPoseHand, which
 * writes the arm rotation only while the entity is using an item with an Annihilator or an Immolator in both hands (both
 * of its branches end in isUsingItem(); bytecode: no write and no setResult before that test). So while every listener is
 * that one (PoseHandEvents.Watch) and the entity is not using an item, posting changes nothing and the handler returns
 * before building the event. Anything else posts as before.
 *
 * Ported to 1.21.1: Cataclysm 3.33 registers onPoseHand (now static) twice as a method reference on NeoForge.EVENT_BUS and
 * keeps its own EventPosePlayerHand.Result; its body and its isUsingItem() gate are unchanged. The listener check reads
 * NeoForge's listener list instead of Forge's (PoseHandEvents); the condition is the same.
 *
 * -Dbons_and_furious.cataclysmPoseHandEvents=false always posts; .shadow=true (verification runs) builds and posts the
 * event itself whenever it would skip (cancelling exactly as Cataclysm's handler does, so behaviour stays stock) and
 * counts any post that changed the model pose or set a result.
 */
public final class CataclysmPoseHand {
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.cataclysmPoseHandEvents", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.cataclysmPoseHandEvents.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    private static volatile PoseHandEvents.Watch watch;
    private static volatile boolean failed, announced;

    private CataclysmPoseHand() {
    }

    private static PoseHandEvents.Watch watch() {
        PoseHandEvents.Watch w = watch;
        if (w != null || failed) return w;
        synchronized (CataclysmPoseHand.class) {
            if (watch != null || failed) return watch;
            try {
                watch = new PoseHandEvents.Watch("cataclysm_pose_hand_events", PoseHandEvents.listenerList(EventPosePlayerHand.class),
                        PoseHandEvents.CATACLYSM_CLIENT_EVENT);
            } catch (Throwable t) {
                failed = true;
                PoseHandEvents.LOGGER.warn("Bons and Furious: cataclysm_pose_hand_events stands down ({}); Cataclysm posts its arm-pose event as before", t.toString());
            }
            return watch;
        }
    }

    /**
     * Called at the head of Cataclysm's handler: true when the handler should return at once (its event would change
     * nothing). In shadow mode the event is built and posted here instead (theirCi cancelled on ALLOW, as Cataclysm does).
     */
    public static boolean skip(LivingEntity entity, HumanoidModel<?> model, boolean left, CallbackInfo theirCi) {
        if (!enabled) return false;
        PoseHandEvents.Watch w = watch();
        if (w == null) return false;
        int kinds = w.kinds();
        if (kinds < 0) return false;
        if ((kinds & PoseHandEvents.CATACLYSM_CLIENT_EVENT) != 0 && entity.isUsingItem()) return false;
        if (!announced) {
            announced = true;
            PoseHandEvents.LOGGER.info("Bons and Furious: cataclysm_pose_hand_events applies (Cataclysm's arm-pose event is built and posted only when a listener can act){}",
                    SHADOW ? " - shadow verification on" : "");
        }
        if (SHADOW) {
            int[] before = PoseHandEvents.snapshot(model);
            EventPosePlayerHand event = new EventPosePlayerHand(entity, model, left);
            NeoForge.EVENT_BUS.post(event);
            SHADOW_CHECKS.incrementAndGet();
            if (event.getResult() == EventPosePlayerHand.Result.ALLOW) theirCi.cancel();
            if (event.getResult() != EventPosePlayerHand.Result.DEFAULT || !java.util.Arrays.equals(before, PoseHandEvents.snapshot(model)))
                PoseHandEvents.mismatch("cataclysm_pose_hand_events", SHADOW_MISMATCHES, entity + " result " + event.getResult());
        }
        return true;
    }
}
