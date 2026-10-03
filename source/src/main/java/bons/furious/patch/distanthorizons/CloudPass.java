package bons.furious.patch.distanthorizons;

import com.seibel.distanthorizons.core.level.IDhClientLevel;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IClientLevelWrapper;
import java.awt.Color;

/**
 * Bons and Furious switch distanthorizons_cloud_pass_invariants (Distant Horizons 3.3.2).
 *
 * DH draws its clouds as 363 box groups. GlGenericObjectRenderer.render runs each group's pre-render function once per
 * frame, and for every cloud group CloudRenderHandler.preRender asks the DH level for the client level wrapper (a
 * synchronized WeakHashMap lookup) and the wrapper for the cloud colour at the frame's partial tick (a new Color each
 * time). Within one render pass both answers are the same for every group: the client level does not change during a
 * frame, and the cloud colour depends only on that level's time, rain, thunder and lightning state at the partial tick,
 * none of which a render pass changes.
 *
 * Inside a pass (the render method's start to its return, render thread only) the first answer is remembered and the
 * other groups of that pass receive it: the same wrapper object, and the same Color object (an immutable value) where each
 * group used to get an equal new one. A different DH level, wrapper or partial tick is asked for real; outside a pass
 * (or with the runtime switch off) every call goes through.
 */
public final class CloudPass {
    /** Runtime switch (the config switch acts when classes are transformed). -Dbons_and_furious.dhCloudPass=false also turns it off. */
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.dhCloudPass", "true"));
    // written by the thread that runs the pass; any other thread always calls through
    private static volatile Thread passThread;
    private static int pass;
    private static boolean active;
    private static int wrapperPass = -1, colorPass = -1, colorTicks;
    private static IDhClientLevel wrapperLevel;
    private static IClientLevelWrapper wrapper, colorWrapper;
    private static Color color;

    private CloudPass() {
    }

    /** Start of GlGenericObjectRenderer.render. */
    public static void begin() {
        passThread = Thread.currentThread();
        pass++;
        active = enabled;
    }

    /** Every return of GlGenericObjectRenderer.render; drops the remembered objects. */
    public static void end() {
        if (passThread != Thread.currentThread()) return;
        active = false;
        wrapperLevel = null;
        wrapper = null;
        colorWrapper = null;
        color = null;
    }

    /** In place of level.getClientLevelWrapper() inside preRender. */
    public static IClientLevelWrapper clientLevelWrapper(IDhClientLevel level) {
        boolean mine = active && passThread == Thread.currentThread();
        if (mine && wrapperPass == pass && wrapperLevel == level) return wrapper;
        IClientLevelWrapper w = level.getClientLevelWrapper();
        if (mine) {
            wrapperPass = pass;
            wrapperLevel = level;
            wrapper = w;
        }
        return w;
    }

    /** In place of wrapper.getCloudColor(partialTicks) inside preRender. */
    public static Color cloudColor(IClientLevelWrapper w, float partialTicks) {
        int bits = Float.floatToRawIntBits(partialTicks);
        boolean mine = active && passThread == Thread.currentThread();
        if (mine && colorPass == pass && colorWrapper == w && colorTicks == bits) return color;
        Color c = w.getCloudColor(partialTicks);
        if (mine) {
            colorPass = pass;
            colorWrapper = w;
            colorTicks = bits;
            color = c;
        }
        return c;
    }
}
