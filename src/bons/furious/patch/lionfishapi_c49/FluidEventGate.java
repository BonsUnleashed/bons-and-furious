package bons.furious.patch.lionfishapi_c49;

import com.github.L_Ender.lionfishapi.client.event.EventGetFluidRenderType;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.level.material.FluidState;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.Event;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch lionfishapi_fluid_render_event_gate (Lionfish API 2.8, also checked on 3.0; client). Lionfish
 * API is LGPL; no Lionfish code is carried, only an early return in front of its handler. Ours, idea from Collections Of
 * Optimizations (lionfishapi.skipIdleFluidRenderEvent), idea text only.
 *
 * What Lionfish API does: its ItemBlockRenderTypesMixin.lionfish_getFluidRenderLayer runs at the end of every
 * ItemBlockRenderTypes.getRenderLayer(FluidState) call, which the chunk mesher makes for every water and lava block of every
 * section it builds (builder threads). Each run builds an EventGetFluidRenderType, posts it on MinecraftForge.EVENT_BUS and
 * sets the returned layer only when a listener answered ALLOW.
 *
 * What the switch does: the handler returns at once while the live listener array of that event on MinecraftForge.EVENT_BUS
 * (the array the next post walks, read by the shared listener census of forge_listener_guard, which recomputes its verdict
 * whenever the bus builds a new array, i.e. whenever any listener is registered or removed) holds nothing but EventPriority
 * phase markers. Why identical: such a post only sets the event's phase field; the result stays DEFAULT, so the handler
 * never sets a layer, and the event object is seen by nobody. With any real listener, or when the census cannot answer
 * (census off, unknown bus shape, class missing), Lionfish's handler runs as shipped.
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

    /** Created on the first gated call (the census looks the event's listener list up through EventListenerHelper). */
    private static final class Watch {
        static final Object W = ListenerCensusLink.watch(EventGetFluidRenderType.class, null);
    }

    /** True when Lionfish's handler may return at once: no real listener for its event on the Forge bus right now. */
    public static boolean skip(FluidState state, RenderType layer) {
        if (!enabled || !ListenerCensusLink.onlyPhaseMarkers(Watch.W)) return false;
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: lionfishapi_fluid_render_event_gate applies (no listener for Lionfish API's fluid render layer event: it is not built per fluid block){}",
                    SHADOW ? " - shadow verification on" : "");
        }
        if (SHADOW) {
            EventGetFluidRenderType probe = new EventGetFluidRenderType(state, layer);
            MinecraftForge.EVENT_BUS.post(probe);
            SHADOW_CHECKS.incrementAndGet();
            if (probe.getResult() != Event.Result.DEFAULT || probe.getRenderType() != layer) {
                long m = SHADOW_MISMATCHES.incrementAndGet();
                if (m <= 20) LOGGER.warn("Bons and Furious: lionfishapi_fluid_render_event_gate shadow mismatch #{}: the probe event came back {} with layer {} (stock layer {})",
                        m, probe.getResult(), probe.getRenderType(), layer);
            }
            return false;
        }
        return true;
    }
}
