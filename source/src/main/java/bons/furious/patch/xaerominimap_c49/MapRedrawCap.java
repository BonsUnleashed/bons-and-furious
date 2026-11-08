package bons.furious.patch.xaerominimap_c49;

import bons.pure.config.PureConfig;
import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.apache.logging.log4j.LogManager;
import xaero.common.minimap.MinimapProcessor;
import xaero.common.minimap.write.MinimapWriter;

/**
 * Bons and Furious switch xaerominimap_map_redraw_cap (Xaero's Minimap 26.6.0 for NeoForge 1.21.1, tested build; client;
 * since 1.0.49). Kind: change (trade-off, off by default). Idea: Collections Of Optimizations (xaerominimap.renderFpsCap),
 * idea text only. Xaero's Minimap is All Rights Reserved: none of its code is carried; the wrapper only decides whether its
 * redraw runs.
 *
 * MinimapFBORenderer.renderChunksToFBO redraws the minimap picture on every frame: the map chunk textures and the chunk grid
 * into one framebuffer, the rotated copy into another, then every map element renderer (radar dots, waypoints, tracked
 * players). The switch (MixinExtras @WrapMethod, so other mods' code inside the method is left out with it) lets the redraw
 * run at most `value` times per second: on other frames the kept picture in the rotation framebuffer is shown again, as
 * renderChunks binds it after the call either way. Any change of what the picture is made from in a way that is not a
 * smooth motion redraws at once: either framebuffer object or its size, the view size, shape, north lock, cave level, world
 * map use, cave mode, map dimension and its scale, zoom, the map writer's loaded area, its origin and level count.
 * Trade-off: the minimap picture (terrain, its rotation and position, radar dots and waypoints on it) changes at most
 * `value` times per second.
 *
 * -Dbons_and_furious.xaerominimapMapRedrawCap=false: emergency off. The value is read on the switch-on path only.
 */
public final class MapRedrawCap {
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.xaerominimapMapRedrawCap", "true"));
    /** Counters for the harness and the rig (render thread). */
    public static long redraws, kept;

    private static long last = Long.MIN_VALUE;
    private static Object scaling, rotation, dimension, blocks;
    private static int sw, sh, rw, rh, view, lvl, shp, lx, lz, levels;
    private static boolean world, north, cave;
    private static double dimScale, zoom;
    private static volatile boolean announced;

    private MapRedrawCap() {
    }

    private static final class Value {   // read on the first switch-on use only
        static final long INTERVAL_NS = 1_000_000_000L / PureConfig.intValue("xaerominimap_map_redraw_cap", 60, 1, 1000);
    }

    /** True when renderChunksToFBO must run on this frame (and the inputs are remembered), false to keep the picture. */
    public static boolean redraw(RenderTarget scalingFb, RenderTarget rotationFb, double zoomNow, MinimapProcessor minimap, ResourceKey<Level> mapDimension,
                                 double mapDimensionScale, int viewW, int level, boolean useWorldMap, boolean lockedNorth, int shape, boolean caveMode) {
        if (!enabled) return true;
        long now = System.nanoTime();
        MinimapWriter writer = minimap == null ? null : minimap.getMinimapWriter();
        Object loaded = writer == null ? null : writer.getLoadedBlocks();
        int loadedX = writer == null ? 0 : writer.getLoadedMapChunkX(), loadedZ = writer == null ? 0 : writer.getLoadedMapChunkZ();
        int loadedLevels = writer == null ? 0 : writer.getLoadedLevels();
        boolean same = last != Long.MIN_VALUE && scalingFb == scaling && rotationFb == rotation && scalingFb != null && rotationFb != null
                && scalingFb.width == sw && scalingFb.height == sh && rotationFb.width == rw && rotationFb.height == rh
                && viewW == view && level == lvl && useWorldMap == world && lockedNorth == north && shape == shp && caveMode == cave
                && mapDimension == dimension && Double.compare(mapDimensionScale, dimScale) == 0 && Double.compare(zoomNow, zoom) == 0
                && loaded == blocks && loadedX == lx && loadedZ == lz && loadedLevels == levels;
        if (same && now - last < Value.INTERVAL_NS) {
            if (!announced) announce();
            kept++;
            return false;
        }
        last = now;
        scaling = scalingFb;
        rotation = rotationFb;
        if (scalingFb != null) {
            sw = scalingFb.width;
            sh = scalingFb.height;
        }
        if (rotationFb != null) {
            rw = rotationFb.width;
            rh = rotationFb.height;
        }
        view = viewW;
        lvl = level;
        world = useWorldMap;
        north = lockedNorth;
        shp = shape;
        cave = caveMode;
        dimension = mapDimension;
        dimScale = mapDimensionScale;
        zoom = zoomNow;
        blocks = loaded;
        lx = loadedX;
        lz = loadedZ;
        levels = loadedLevels;
        redraws++;
        return true;
    }

    private static synchronized void announce() {
        if (announced) return;
        announced = true;
        LogManager.getLogger("Bons and Furious").info("Bons and Furious: xaerominimap_map_redraw_cap applies (the minimap picture is redrawn at most "
                + 1_000_000_000L / Value.INTERVAL_NS + " times per second)");
    }
}
