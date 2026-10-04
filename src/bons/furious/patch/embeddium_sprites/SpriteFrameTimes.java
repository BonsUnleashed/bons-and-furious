package bons.furious.patch.embeddium_sprites;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch embeddium_sprite_tick_frame_times (Embeddium 0.3.31+mc1.20.1 on Minecraft 1.20.1, client only).
 * Helper of bons.furious.mixin.embeddium_sprites.SpriteTickerFrameTimesMixin. SRG member names.
 *
 * With Embeddium's "animate only visible textures" (on in this pack), every animated sprite that was not drawn since the
 * last tick still has its frame counters advanced every client tick by Embeddium's SpriteContentsAnimatorImplMixin.preTick
 * (++subFrame; if subFrame >= frames.get(frame).time then frame = (frame + 1) % frames.size(), subFrame = 0). That reads
 * Ticker -> AnimatedTexture -> frames list -> its backing array -> FrameInfo, four dependent objects per sprite per tick,
 * for every one of the pack's ~2,000-3,000 animated sprites; between ticks the render thread evicts them all, so each tick
 * pays that chain in cache misses (2.1-4.9% of the render thread in review 8's client recording).
 *
 * The frames list of an AnimatedTexture is a final field holding an immutable list (vanilla: Guava ImmutableList.copyOf;
 * Fusion: java.util.List.copyOf) of FrameInfo objects whose time field is final, so the frame times of a ticker never
 * change. snapshot() copies them once per ticker into an int[]; the mixin then advances the counters with times[frame] and
 * times.length in place of frames.get(frame).time and frames.size(): the same reads and writes of the ticker's frame and
 * subFrame fields, in the same order, with the same values. Lists of any other class, empty lists and null entries are
 * never copied (NONE): those tickers, and any frame index outside the list, go through Embeddium's handler unchanged.
 */
public final class SpriteFrameTimes {
    /** Runtime switch (the config switch acts when classes are transformed). -Dbons_and_furious.spriteTickFrameTimes=false also turns it off. */
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.spriteTickFrameTimes", "true"));
    /**
     * Rig self-check: -Dbons_and_furious.spriteTickFrameTimes.shadow=true leaves every tick to Embeddium's handler and only
     * compares the copied frame time and frame count with the live list on each call that the fast path would have taken.
     */
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.spriteTickFrameTimes.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    /** Tickers that got a frame-time copy, and tickers left to Embeddium's handler (unknown list class, empty, nulls). */
    public static final AtomicLong SNAPSHOTS = new AtomicLong(), SKIPPED = new AtomicLong();
    /** The marker for a ticker whose frames are not copied: every frame index is out of its range. */
    public static final int[] NONE = new int[0];

    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    private static final MethodHandle ANIMATION, FRAMES, TIME;
    private static final boolean READY;
    private static final Class<?> JDK_LIST12 = List.of(1).getClass(), JDK_LISTN = List.of(1, 2, 3).getClass();
    private static volatile boolean announced;

    static {
        MethodHandle a = null, f = null, t = null;
        boolean ready = false;
        try {
            ClassLoader cl = SpriteFrameTimes.class.getClassLoader();
            Class<?> ticker = Class.forName("net.minecraft.client.renderer.texture.SpriteContents$Ticker", false, cl);
            Class<?> animated = Class.forName("net.minecraft.client.renderer.texture.SpriteContents$AnimatedTexture", false, cl);
            Class<?> frameInfo = Class.forName("net.minecraft.client.renderer.texture.SpriteContents$FrameInfo", false, cl);
            Field animation = ticker.getDeclaredField("f_243921_");        // Ticker.animationInfo (final)
            Field frames = animated.getDeclaredField("f_243714_");         // AnimatedTexture.frames (final)
            Field time = frameInfo.getDeclaredField("f_244553_");          // FrameInfo.time (final)
            for (Field x : new Field[]{animation, frames, time}) x.setAccessible(true);
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            a = lookup.unreflectGetter(animation).asType(MethodType.methodType(Object.class, Object.class));
            f = lookup.unreflectGetter(frames).asType(MethodType.methodType(Object.class, Object.class));
            t = lookup.unreflectGetter(time).asType(MethodType.methodType(int.class, Object.class));
            ready = true;
        } catch (Throwable e) {
            LOGGER.warn("Bons and Furious: embeddium_sprite_tick_frame_times is inactive because the sprite animation classes are not the supported "
                    + "1.20.1 layout ({})", e.toString());
        }
        ANIMATION = a;
        FRAMES = f;
        TIME = t;
        READY = ready;
    }

    private SpriteFrameTimes() {
    }

    /**
     * The frame times of a ticker (a SpriteContents$Ticker), copied from its immutable frames list, or NONE when the list
     * is not one of the immutable list classes, is empty or holds a null (then Embeddium's handler keeps handling it).
     */
    public static int[] snapshot(Object ticker) {
        if (!READY) return NONE;
        try {
            Object animation = (Object) ANIMATION.invokeExact(ticker);
            Object frames = animation == null ? null : (Object) FRAMES.invokeExact(animation);
            if (!(frames instanceof List<?> list) || !immutable(list) || list.isEmpty()) return skipped();
            int[] times = new int[list.size()];
            for (int i = 0; i < times.length; i++) {
                Object frame = list.get(i);
                if (frame == null) return skipped();
                times[i] = (int) TIME.invokeExact(frame);
            }
            SNAPSHOTS.incrementAndGet();
            if (!announced) {
                announced = true;
                LOGGER.info("Bons and Furious: embeddium_sprite_tick_frame_times: invisible animated sprites advance their frames from a copy of their "
                        + "frame times{}", SHADOW ? " (SHADOW mode: comparing only)" : "");
            }
            return times;
        } catch (Throwable e) {
            return skipped();
        }
    }

    /** Guava's ImmutableList (vanilla's copyOf) or the JDK's List.of / List.copyOf lists (Fusion): contents can never change. */
    static boolean immutable(List<?> list) {
        Class<?> c = list.getClass();
        return list instanceof com.google.common.collect.ImmutableList<?> || c == JDK_LIST12 || c == JDK_LISTN;
    }

    private static int[] skipped() {
        SKIPPED.incrementAndGet();
        return NONE;
    }

    /**
     * SHADOW mode: compares times[frame] and times.length with frames.get(frame).time and frames.size() of the live list.
     * Called instead of the fast path; Embeddium's handler then runs as shipped.
     */
    public static void shadowCheck(Object ticker, int frame, int[] times) {
        SHADOW_CHECKS.incrementAndGet();
        try {
            List<?> list = (List<?>) (Object) FRAMES.invokeExact((Object) ANIMATION.invokeExact(ticker));
            int live = (int) TIME.invokeExact((Object) list.get(frame));
            if (live != times[frame] || list.size() != times.length) mismatch(frame, live, times[frame], list.size(), times.length);
        } catch (Throwable e) {
            mismatch(frame, -1, times[frame], -1, times.length);
        }
    }

    private static void mismatch(int frame, int live, int copied, int liveSize, int copiedSize) {
        if (SHADOW_MISMATCHES.incrementAndGet() <= 20) {
            LOGGER.warn("Bons and Furious: embeddium_sprite_tick_frame_times SHADOW mismatch at frame {}: live time {} vs copy {}, live size {} vs copy {}",
                    frame, live, copied, liveSize, copiedSize);
        }
    }
}
