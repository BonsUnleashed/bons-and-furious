package bons.furious.patch.xaerominimap_c49;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.IdentityHashMap;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import xaero.hud.minimap.element.render.MinimapElementRenderProvider;
import xaero.hud.minimap.element.render.MinimapElementRenderer;

/**
 * Bons and Furious switch xaerominimap_empty_element_renderer_skip (Xaero's Minimap 26.6.0 for NeoForge 1.21.1, tested build;
 * client; since 1.0.49). Kind: change (trade-off, off by default). Idea: Collections Of Optimizations
 * (xaerominimap.skipEmptyElementRenderers), idea text only. Xaero's Minimap is All Rights Reserved: none of its code is
 * carried; the wrappers only decide whether an element provider's walk runs.
 *
 * Every frame, for each element renderer of each element handler (waypoints in the world, waypoints, radar dots and
 * tracked players on the minimap), MinimapElementRendererHandler.renderForRenderer runs the renderer's preRender, then its
 * provider's walk (begin: e.g. every waypoint collected, filtered and sorted; then each element tested and drawn), then
 * postRender. The switch counts what each renderer of Xaero's Minimap (the four above, by exact class) drew on the first
 * frame of each client tick; on the other frames of that tick a renderer that drew nothing keeps its preRender and
 * postRender (so the frame's buffer flushes, depth state and per-frame resets are Xaero's own) but its provider's begin,
 * element walk and end are left out. Renderers of other mods are never left out. A radar list rebuild outside the tick starts a new count.
 * Trade-off: an element that comes into view between two client ticks appears up to one tick (50 ms) later; the waypoint
 * provider's per-frame work (e.g. removing a reached deathpoint) runs on the first frame of each tick only.
 *
 * -Dbons_and_furious.xaerominimapEmptyElementRendererSkip=false: emergency off.
 */
public final class EmptyElementSkip {
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.xaerominimapEmptyElementRendererSkip", "true"));
    /** Counters for the harness and the rig (render thread): full runs, runs without the provider walk. */
    public static long full, walksLeftOut;

    /** The classes Xaero's Minimap 26.6.0 builds its four renderers as (their Builders; the waypoint and radar ones are the
     *  compatibility subclasses of the abstract renderers). Exact class match: a subclass made by another mod never skips. */
    static final Set<String> AUDITED = Set.of("xaero.common.minimap.waypoints.render.WaypointsIngameRenderer",
            "xaero.common.minimap.waypoints.render.WaypointsGuiRenderer", "xaero.common.minimap.render.radar.element.RadarRenderer",
            "xaero.hud.minimap.player.tracker.PlayerTrackerMinimapElementRenderer");
    private static final ClassValue<Boolean> OURS = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            return AUDITED.contains(type.getName());
        }
    };

    static long tick;
    private static MinimapElementRenderProvider<?, ?> skipProvider;
    private static volatile boolean announced;

    private EmptyElementSkip() {
    }

    /** MinimapProcessor.onClientTick HEAD: a client tick. */
    public static void tick() {
        tick++;
    }

    /** The radar lists were rebuilt outside a client tick: every renderer counts again. */
    public static void listsChanged() {
        tick++;
    }

    /** The handler's `renderForRenderer(renderer, ..)` call in render; seen: the handler's own (renderer -> {tick, drawn}). */
    public static int render(IdentityHashMap<Object, long[]> seen, MinimapElementRenderer<?, ?> renderer, int elementIndex, Operation<Integer> original,
                             Object... args) {
        if (!enabled || renderer == null || !OURS.get(renderer.getClass())) return original.call(args);
        long[] st = seen.get(renderer);
        if (st == null) seen.put(renderer, st = new long[] {Long.MIN_VALUE, 0});
        long t = tick;
        if (st[0] != t) {
            int n = original.call(args);
            st[0] = t;
            st[1] = n - elementIndex;
            full++;
            return n;
        }
        if (st[1] != 0) return original.call(args);
        if (!announced) announce();
        MinimapElementRenderProvider<?, ?> before = skipProvider;
        skipProvider = renderer.getProvider();
        walksLeftOut++;
        try {
            return original.call(args);
        } finally {
            skipProvider = before;
        }
    }

    /** renderForRenderer's provider.begin / hasNext / end: true when this provider's walk is left out on this frame. */
    public static boolean leftOut(MinimapElementRenderProvider<?, ?> provider) {
        return provider != null && provider == skipProvider;
    }

    private static synchronized void announce() {
        if (announced) return;
        announced = true;
        LogManager.getLogger("Bons and Furious").info("Bons and Furious: xaerominimap_empty_element_renderer_skip applies (a minimap element renderer that drew nothing on a tick's first frame skips its element walk until the next tick)");
    }
}
