package bons.furious.patch.cataclysm_client;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.ASMEventHandler;
import net.minecraftforge.eventbus.EventBus;
import net.minecraftforge.eventbus.ListenerList;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventListener;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Shared part of Bons and Furious switches cataclysm_pose_hand_events and citadel_pose_hand_events (since 1.0.36, client).
 * Ours entirely.
 *
 * L_Ender's Cataclysm and Citadel each merge a HumanoidModelMixin into HumanoidModel.poseRightArm / poseLeftArm whose
 * handler builds an EventPosePlayerHand (their own class each), posts it on MinecraftForge.EVENT_BUS and cancels the
 * vanilla arm pose when a listener set the result to ALLOW: four event objects and four bus posts per humanoid model per
 * frame. When every listener on the bus is a known one and none of them can act for this entity (their own tests, mirrored
 * here as pure reads that are a superset of theirs), the post changes nothing (no listener writes, the result stays
 * DEFAULT, the handler does not cancel), so the handler may return before building the event.
 *
 * The listener set is the bus's own: the event class's ListenerList (taken from one event instance, exactly what
 * EventBus.post asks) for the Forge bus's id. A Watch classifies the listener array once per array object (the list
 * replaces its volatile array whenever a listener is added or removed): EventPriority phase markers are ignored, every
 * other entry must be an ASMEventHandler whose description ("ASM: <owner class>@<hash> <method><descriptor>") names one of
 * the known listener methods; anything else (another mod's listener, a lambda listener, a listener on Event itself)
 * makes the switch post as before.
 */
public final class PoseHandEvents {
    static final Logger LOGGER = LogManager.getLogger("Bons and Furious");

    /** Known listener methods (bits); UNKNOWN when the array holds anything else. */
    static final int CATACLYSM_CLIENT_EVENT = 1, ALEXSCAVES_CLIENT_EVENTS = 2, ALEXSMOBS_CLIENT_EVENTS = 4, UNKNOWN = -1;

    private PoseHandEvents() {
    }

    /** The classification of one listener array (immutable pair, so a racing reader sees it whole). */
    record Classified(IEventListener[] array, int kinds) {}

    /** One event class on the Forge bus. */
    static final class Watch {
        final String key;
        final ListenerList list;
        final int busId;
        final int allowed;
        volatile Classified seen;

        Watch(String key, ListenerList list, int busId, int allowed) {
            this.key = key;
            this.list = list;
            this.busId = busId;
            this.allowed = allowed;
        }

        /** Bits of the known listeners now registered, or UNKNOWN. */
        int kinds() {
            IEventListener[] now = this.list.getListeners(this.busId);
            Classified c = this.seen;
            if (c == null || c.array() != now) {
                c = new Classified(now, classify(now, this.allowed));
                this.seen = c;
                LOGGER.debug("Bons and Furious: {}: {} listener(s) -> {}", this.key, now.length, c.kinds());
            }
            return c.kinds();
        }
    }

    static int busId() throws ReflectiveOperationException {
        if (!(MinecraftForge.EVENT_BUS instanceof EventBus bus)) throw new IllegalStateException("the Forge bus is a " + MinecraftForge.EVENT_BUS.getClass().getName());
        Field id = EventBus.class.getDeclaredField("busID");
        id.setAccessible(true);
        return id.getInt(bus);
    }

    static int classify(IEventListener[] listeners, int allowed) {
        int kinds = 0;
        for (IEventListener l : listeners) {
            if (l instanceof EventPriority) continue;
            int k = known(l);
            if (k == 0 || (k & allowed) == 0) return UNKNOWN;
            kinds |= k;
        }
        return kinds;
    }

    static int known(IEventListener l) {
        if (!(l instanceof ASMEventHandler)) return 0;
        String s = l.toString();
        if (names(s, "com.github.L_Ender.cataclysm.client.event.ClientEvent", "onPoseHand(Lcom/github/L_Ender/cataclysm/client/event/EventPosePlayerHand;)V"))
            return CATACLYSM_CLIENT_EVENT;
        if (names(s, "com.github.alexmodguy.alexscaves.client.event.ClientEvents", "onPoseHand(Lcom/github/alexthe666/citadel/client/event/EventPosePlayerHand;)V"))
            return ALEXSCAVES_CLIENT_EVENTS;
        if (names(s, "com.github.alexthe666.alexsmobs.client.event.ClientEvents", "onPoseHand(Lcom/github/alexthe666/citadel/client/event/EventPosePlayerHand;)V"))
            return ALEXSMOBS_CLIENT_EVENTS;
        return 0;
    }

    /** "ASM: " + owner + "@" + hex hash + " " + method + descriptor: an instance of exactly that class (default toString). */
    static boolean names(String s, String owner, String methodDesc) {
        String head = "ASM: " + owner + "@";
        if (!s.startsWith(head) || !s.endsWith(" " + methodDesc)) return false;
        int from = head.length(), to = s.length() - methodDesc.length() - 1;
        if (to <= from) return false;
        for (int i = from; i < to; i++) if (Character.digit(s.charAt(i), 16) < 0) return false;
        return true;
    }

    // ---------------------------------------------------------------- shadow verification helpers

    static ModelPart[] parts(HumanoidModel<?> m) {
        return new ModelPart[]{m.f_102808_, m.f_102809_, m.f_102810_, m.f_102811_, m.f_102812_, m.f_102813_, m.f_102814_};
    }

    /** The pose of every part of the humanoid model (position, rotation, scale, visibility) as raw bits. */
    static int[] snapshot(HumanoidModel<?> m) {
        ModelPart[] ps = parts(m);
        int[] out = new int[ps.length * 11];
        int k = 0;
        for (ModelPart p : ps) {
            out[k++] = Float.floatToRawIntBits(p.f_104200_);
            out[k++] = Float.floatToRawIntBits(p.f_104201_);
            out[k++] = Float.floatToRawIntBits(p.f_104202_);
            out[k++] = Float.floatToRawIntBits(p.f_104203_);
            out[k++] = Float.floatToRawIntBits(p.f_104204_);
            out[k++] = Float.floatToRawIntBits(p.f_104205_);
            out[k++] = Float.floatToRawIntBits(p.f_233553_);
            out[k++] = Float.floatToRawIntBits(p.f_233554_);
            out[k++] = Float.floatToRawIntBits(p.f_233555_);
            out[k++] = p.f_104207_ ? 1 : 0;
            out[k++] = p.f_233556_ ? 1 : 0;
        }
        return out;
    }

    static void mismatch(String key, AtomicLong counter, String what) {
        if (counter.incrementAndGet() <= 20) LOGGER.warn("Bons and Furious: {} shadow mismatch: {}", key, what);
    }
}
