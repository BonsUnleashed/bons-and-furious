package bons.furious.patch.cataclysm_client;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.neoforged.bus.ConsumerEventHandler;
import net.neoforged.bus.EventBus;
import net.neoforged.bus.ListenerList;
import net.neoforged.bus.api.EventListener;
import net.neoforged.neoforge.common.NeoForge;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Shared part of Bons and Furious switches cataclysm_pose_hand_events and citadel_pose_hand_events (since 1.0.36, client;
 * 1.21.1 tested builds L_Ender's Cataclysm 1.21.1-3.33, citadel-2.7.1-1.21.1, NeoForge 21.1.252 with its event bus 8.0.5).
 * Ours entirely.
 *
 * L_Ender's Cataclysm and Citadel each merge a HumanoidModelMixin into HumanoidModel.poseRightArm / poseLeftArm whose
 * handler builds an EventPosePlayerHand (their own class each), posts it on NeoForge.EVENT_BUS and cancels the vanilla arm
 * pose when a listener set the result to allow: four event objects and four bus posts per humanoid model per frame. When
 * every listener on the bus is a known one and none of them can act for this entity (their own tests, mirrored here as pure
 * reads that are a superset of theirs), the post changes nothing (no listener writes, the result stays default, the handler
 * does not cancel), so the handler may return before building the event.
 *
 * The listener set is the bus's own: the ListenerList NeoForge.EVENT_BUS keeps for the event class (EventBus.post asks
 * exactly that list for the posted event's class; the bus creates it once and keeps it). A Watch classifies the list's
 * listener array once per array object (the list builds a new array whenever a listener is added or removed): every entry
 * must be a known listener, anything else (another mod's listener, an @SubscribeEvent method, a filtered listener) makes
 * the switch post as before. An empty array means a post invokes nothing.
 *
 * Ported to 1.21.1: NeoForge's bus has no ASMEventHandler descriptions, no bus ids and no EventPriority markers in the
 * array. Cataclysm 3.33 registers its listener as a method reference (NeoForge.EVENT_BUS.addListener(ClientEvent::onPoseHand),
 * twice, in the static ClientEvent.ClientEvent()); for an event that is not cancellable the bus keeps such a listener as a
 * plain ConsumerEventHandler around the method reference's lambda object. That lambda is recognised by its class: a hidden
 * synthetic class whose nest host is ClientEvent, i.e. made by an invokedynamic in ClientEvent's own code. ClientEvent
 * 3.33 (guarded: its registration method, its listener and its declared-method set) has exactly one method that takes the
 * event, onPoseHand, and registers it only there, so such a lambda in this event's list is that listener. Citadel's event
 * has no known listener on 1.21.1 (Alex's Caves and Alex's Mobs, the two 1.0.36 recognised, have no NeoForge 1.21.1 build).
 * Reading the list and the lambda goes through reflection on the bus (an open module); any failure makes the switch stand
 * down.
 */
public final class PoseHandEvents {
    static final Logger LOGGER = LogManager.getLogger("Bons and Furious");

    /** Known listener methods (bits); UNKNOWN when the array holds anything else. */
    static final int CATACLYSM_CLIENT_EVENT = 1, UNKNOWN = -1;

    /** The class whose own method references to onPoseHand are Cataclysm's listener (compared by name: Cataclysm may be absent). */
    static final String CATACLYSM_CLIENT_EVENT_CLASS = "com.github.L_Ender.cataclysm.client.event.ClientEvent";

    private static volatile Field consumerField;

    private PoseHandEvents() {
    }

    /** The classification of one listener array (immutable pair, so a racing reader sees it whole). */
    record Classified(EventListener[] array, int kinds) {}

    /** One event class on NeoForge.EVENT_BUS. */
    static final class Watch {
        final String key;
        final ListenerList list;
        final int allowed;
        volatile Classified seen;

        Watch(String key, ListenerList list, int allowed) {
            this.key = key;
            this.list = list;
            this.allowed = allowed;
        }

        /** Bits of the known listeners now registered (0: none at all), or UNKNOWN. */
        int kinds() {
            EventListener[] now = this.list.getListeners();
            Classified c = this.seen;
            if (c == null || c.array() != now) {
                c = new Classified(now, classify(now, this.allowed));
                this.seen = c;
                LOGGER.debug("Bons and Furious: {}: {} listener(s) -> {}", this.key, now.length, c.kinds());
            }
            return c.kinds();
        }
    }

    /** The ListenerList NeoForge.EVENT_BUS posts this event class to (EventBus.getListenerList, private). */
    static ListenerList listenerList(Class<?> eventClass) throws ReflectiveOperationException {
        if (!(NeoForge.EVENT_BUS instanceof EventBus bus)) throw new IllegalStateException("the NeoForge bus is a " + NeoForge.EVENT_BUS.getClass().getName());
        Method get = EventBus.class.getDeclaredMethod("getListenerList", Class.class);
        get.setAccessible(true);
        return (ListenerList) get.invoke(bus, eventClass);
    }

    static int classify(EventListener[] listeners, int allowed) {
        int kinds = 0;
        for (EventListener l : listeners) {
            int k = known(l);
            if (k == 0 || (k & allowed) == 0) return UNKNOWN;
            kinds |= k;
        }
        return kinds;
    }

    static int known(EventListener l) {
        if (l == null || l.getClass() != ConsumerEventHandler.class) return 0;
        Object consumer;
        try {
            consumer = consumerField().get(l);
        } catch (Throwable t) {
            LOGGER.debug("Bons and Furious: cannot read a NeoForge listener ({}); treated as unknown", t.toString());
            return 0;
        }
        if (consumer == null) return 0;
        Class<?> type = consumer.getClass();
        if (!type.isHidden() || !type.isSynthetic()) return 0;
        if (CATACLYSM_CLIENT_EVENT_CLASS.equals(type.getNestHost().getName())) return CATACLYSM_CLIENT_EVENT;
        return 0;
    }

    private static Field consumerField() throws ReflectiveOperationException {
        Field f = consumerField;
        if (f == null) {
            f = ConsumerEventHandler.class.getDeclaredField("consumer");
            f.setAccessible(true);
            consumerField = f;
        }
        return f;
    }

    // ---------------------------------------------------------------- shadow verification helpers

    static ModelPart[] parts(HumanoidModel<?> m) {
        return new ModelPart[]{m.head, m.hat, m.body, m.rightArm, m.leftArm, m.rightLeg, m.leftLeg};
    }

    /** The pose of every part of the humanoid model (position, rotation, scale, visibility) as raw bits. */
    static int[] snapshot(HumanoidModel<?> m) {
        ModelPart[] ps = parts(m);
        int[] out = new int[ps.length * 11];
        int k = 0;
        for (ModelPart p : ps) {
            out[k++] = Float.floatToRawIntBits(p.x);
            out[k++] = Float.floatToRawIntBits(p.y);
            out[k++] = Float.floatToRawIntBits(p.z);
            out[k++] = Float.floatToRawIntBits(p.xRot);
            out[k++] = Float.floatToRawIntBits(p.yRot);
            out[k++] = Float.floatToRawIntBits(p.zRot);
            out[k++] = Float.floatToRawIntBits(p.xScale);
            out[k++] = Float.floatToRawIntBits(p.yScale);
            out[k++] = Float.floatToRawIntBits(p.zScale);
            out[k++] = p.visible ? 1 : 0;
            out[k++] = p.skipDraw ? 1 : 0;
        }
        return out;
    }

    static void mismatch(String key, AtomicLong counter, String what) {
        if (counter.incrementAndGet() <= 20) LOGGER.warn("Bons and Furious: {} shadow mismatch: {}", key, what);
    }
}
