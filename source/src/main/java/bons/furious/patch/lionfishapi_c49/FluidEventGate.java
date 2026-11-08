package bons.furious.patch.lionfishapi_c49;

import com.github.L_Ender.lionfishapi.client.event.EventGetFluidRenderType;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.bus.EventBus;
import net.neoforged.bus.ListenerList;
import net.neoforged.neoforge.common.NeoForge;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch lionfishapi_fluid_render_event_gate (Lionfish API 3.1 for Minecraft 1.21.1, NeoForge 21.1.252 with
 * its event bus 8.0.5; client). Lionfish API is LGPL; no Lionfish code is carried, only an early return in front of its
 * handler. Ours, idea from Collections Of Optimizations (lionfishapi.skipIdleFluidRenderEvent), idea text only.
 *
 * What Lionfish API does: its ItemBlockRenderTypesMixin.lionfish_getFluidRenderLayer runs at the end of every
 * ItemBlockRenderTypes.getRenderLayer(FluidState) call, which the chunk mesher makes for every water and lava block of every
 * section it builds (builder threads). Each run builds an EventGetFluidRenderType, posts it on NeoForge.EVENT_BUS and sets
 * the returned layer only when a listener set the event's result to ALLOW.
 *
 * What the switch does: the handler returns at once while the bus's own listener list for that event class holds no
 * listener. The list is the one NeoForge.EVENT_BUS posts the event to (EventBus.getListenerList for the event's class,
 * created once and kept by the bus; it includes the listeners of the event's superclasses); its getListeners() array is
 * what the next post loops over, rebuilt by the list whenever a listener is registered or removed, and is read again on
 * every call. Why identical: a post to an empty array runs the bus's type check (which this event passes) and calls
 * nothing, so the result stays DEFAULT, the handler never sets a layer, and the event object is seen by nobody. With any
 * listener, or when the list cannot be read (another bus class, reflection refused), Lionfish's handler runs as shipped.
 *
 * Flag: -Dbons_and_furious.lionfishapiFluidRenderEventGate=false restores the handler on every call. Shadow
 * (-Dbons_and_furious.lionfishapiFluidRenderEventGate.shadow=true): nothing is skipped; wherever the gate would skip, the
 * same event is posted once more as a probe and must come back DEFAULT with the layer unchanged (SHADOW_CHECKS /
 * SHADOW_MISMATCHES, WARN for the first 20).
 */
public final class FluidEventGate {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.lionfishapiFluidRenderEventGate", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.lionfishapiFluidRenderEventGate.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    private static volatile boolean announced;

    private FluidEventGate() {
    }

    /** Looked up on the first gated call: the listener list NeoForge.EVENT_BUS posts EventGetFluidRenderType to, or null. */
    private static final class Watch {
        static final ListenerList LIST = listenerList();
    }

    private static ListenerList listenerList() {
        try {
            if (!(NeoForge.EVENT_BUS instanceof EventBus bus)) throw new IllegalStateException("the NeoForge bus is a " + NeoForge.EVENT_BUS.getClass().getName());
            Method get = EventBus.class.getDeclaredMethod("getListenerList", Class.class);
            get.setAccessible(true);
            return (ListenerList) get.invoke(bus, EventGetFluidRenderType.class);
        } catch (Throwable t) {
            LOGGER.warn("Bons and Furious: lionfishapi_fluid_render_event_gate stands down ({}); Lionfish API posts its fluid render layer event as before", t.toString());
            return null;
        }
    }

    /** True when Lionfish's handler may return at once: no listener for its event on the NeoForge bus right now. */
    public static boolean skip(FluidState state, RenderType layer) {
        if (!enabled) return false;
        ListenerList list = Watch.LIST;
        if (list == null || list.getListeners().length != 0) return false;
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: lionfishapi_fluid_render_event_gate applies (no listener for Lionfish API's fluid render layer event: it is not built per fluid block){}",
                    SHADOW ? " - shadow verification on" : "");
        }
        if (SHADOW) {
            EventGetFluidRenderType probe = new EventGetFluidRenderType(state, layer);
            NeoForge.EVENT_BUS.post(probe);
            SHADOW_CHECKS.incrementAndGet();
            if (probe.getResult() != EventGetFluidRenderType.Result.DEFAULT || probe.getRenderType() != layer) {
                long m = SHADOW_MISMATCHES.incrementAndGet();
                if (m <= 20) LOGGER.warn("Bons and Furious: lionfishapi_fluid_render_event_gate shadow mismatch #{}: the probe event came back {} with layer {} (stock layer {})",
                        m, probe.getResult(), probe.getRenderType(), layer);
            }
            return false;
        }
        return true;
    }
}
