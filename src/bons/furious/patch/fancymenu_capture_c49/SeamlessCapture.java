package bons.furious.patch.fancymenu_capture_c49;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;
import net.minecraft.Util;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL21;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL32;
import org.lwjgl.opengl.GLCapabilities;
import org.lwjgl.system.MemoryUtil;
import org.spongepowered.asm.mixin.transformer.meta.MixinMerged;

/**
 * Bons and Furious switch fancymenu_seamless_capture_async (FancyMenu 3.9.14, client; exact variant). Ours; no FancyMenu
 * code is carried (FancyMenu's licence allows none).
 *
 * What FancyMenu does. With its "seamless world loading" option on (off by default), SeamlessWorldLoadingHandler
 * .captureFrameIfNeeded keeps a screenshot of the game, at most one per second, as the next loading-screen background:
 * Screenshot.takeScreenshot reads the whole main framebuffer back with a synchronous glGetTexImage (the CPU waits until the
 * GPU has finished the frame so far, then copies 8 MB at 1080p, 33 MB at 4K), sets every pixel's alpha with a per-pixel
 * loop and flips the rows, all on the render thread: a hitch of several to tens of milliseconds every second.
 *
 * What the switch does. At the same call (same point in the frame, same texture bound, same pack alignment) the read goes
 * into a pixel buffer object with a fence instead; FancyMenu's own code goes on with a placeholder image (never shown,
 * never saved), so its one-per-second check and timestamp behave exactly as before. When the fence has signalled (checked
 * at FancyMenu's next captures of later frames) the buffer is mapped and a background thread copies it into a new
 * NativeImage, applies the same alpha fill and the same row flip, then the render thread unmaps it and puts the image where
 * stock put it. Saving the capture on leaving the world first finishes a pending capture on the spot (fence wait), and
 * clearing or changing the capture target drops a pending one, so the saved file is the frame stock saves.
 *
 * Exact variant. GL reads the texture at the same point of the command stream either way, so the bytes are stock's
 * (proven in a GL harness, and in game with the shadow mode). The one internal difference: the image of a capture is
 * ready a few frames after the capture instead of at once; until then FancyMenu holds the placeholder (nothing reads the
 * capture before it is saved, and saving finishes it first). Memory: one pixel buffer of the framebuffer's size.
 *
 * Stands aside (FancyMenu's own capture runs, as without the switch) when another mod hooks the screenshot: on the first
 * capture the merged methods of Screenshot and NativeImage are read once, and an injector handler or an overwritten game
 * method that another mod's mixin merged there (FancyMenu's own NativeImage accessor and other mods' accessors are not
 * hooks) keeps the shipped path for the session; one INFO line names it (see foreignHook).
 *
 * Shadow mode (-Dbons_and_furious.fancymenuSeamlessCaptureAsync.shadow=true, rig only): every capture also takes stock's
 * synchronous screenshot at the same point and compares it with the asynchronous result (SHADOW_CHECKS,
 * SHADOW_MISMATCHES, at most 20 WARN lines).
 *
 * -Dbons_and_furious.fancymenuSeamlessCaptureAsync=false switches it off at run time.
 */
public final class SeamlessCapture {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.fancymenuSeamlessCaptureAsync", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.fancymenuSeamlessCaptureAsync.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    /** Captures started asynchronously, published from a poll, finished on the spot (save), dropped (clear), taken the
     *  stock way, and taken the stock way because another mod hooks the screenshot. */
    public static long started, published, finishedNow, dropped, stockCaptures, stoodAside;
    private static volatile boolean announced, unsupportedLogged;

    /** The placeholder FancyMenu holds while a capture is pending (1 x 1, never closed, never saved). */
    private static NativeImage marker;

    private static final class Pending {
        final int width, height;
        final long size;
        long fence;
        long mapped;
        volatile NativeImage result;
        volatile boolean copying;
        NativeImage shadow;

        Pending(int width, int height) {
            this.width = width;
            this.height = height;
            this.size = (long) width * height * 4L;
        }
    }

    private static Pending pending;
    private static int buffer = -1;
    private static long bufferSize;

    private SeamlessCapture() {}

    public static NativeImage marker() {
        NativeImage m = marker;
        if (m == null) marker = m = new NativeImage(1, 1, false);
        return m;
    }

    public static boolean isMarker(NativeImage image) {
        return image != null && image == marker;
    }

    private static boolean supported() {
        GLCapabilities caps = GL.getCapabilities();
        boolean ok = caps.OpenGL32 && caps.OpenGL30 && caps.OpenGL21;
        if (!ok && !unsupportedLogged) {
            unsupportedLogged = true;
            LOGGER.warn("Bons and Furious: fancymenu_seamless_capture_async falls back to FancyMenu's own capture (no OpenGL 3.2 sync objects)");
        }
        return ok;
    }

    /** Mixin's handler names: prefix, class id (3 or more letters), method id (3 or more hex digits), the handler's name. */
    private static final Pattern HANDLER = Pattern.compile("^[a-zA-Z]+\\$[a-z]{3,}[0-9a-f]{3,}\\$");
    private static final Pattern GAME_METHOD = Pattern.compile("^m_\\d+_$");
    private static final String OURS = "bons.furious.mixin.fancymenu_capture_c49.";
    private static final String FANCYMENU_ACCESSOR = "de.keksuccino.fancymenu.mixin.mixins.common.client.IMixinNativeImage";
    /** null = not looked at yet, "" = no other mod hooks the screenshot, else what was found (then FancyMenu's capture runs). */
    private static volatile String foreign;

    /**
     * Whether another mod hooks the screenshot: read once (on the first capture) from the merged methods of Screenshot and
     * NativeImage. A method another mod's mixin merged there counts when it is an injector handler (Mixin names those
     * {@code <kind>$<id>$<name>}) or an overwritten game method ({@code m_<n>_}): such a mod would not run for an
     * asynchronous capture. Accessors, invokers and added members of other mods (Embeddium's and FancyMenu's own
     * IMixinNativeImage on NativeImage, for example) change no method and are not counted; nor are this group's own mixins.
     * Returns "" or a description of the first hook found.
     */
    public static String foreignHook() {
        String f = foreign;
        if (f == null) {
            f = scan(net.minecraft.client.Screenshot.class);
            if (f.isEmpty()) f = scan(NativeImage.class);
            foreign = f;
            if (!f.isEmpty()) {
                LOGGER.info("Bons and Furious: fancymenu_seamless_capture_async stands aside: another mod hooks the screenshot ({}), so FancyMenu's own capture runs", f);
            }
        }
        return f;
    }

    private static String scan(Class<?> c) {
        try {
            for (Method m : c.getDeclaredMethods()) {
                MixinMerged merged = m.getAnnotation(MixinMerged.class);
                if (merged == null) continue;
                String mixin = merged.mixin();
                if (mixin.startsWith(OURS) || mixin.equals(FANCYMENU_ACCESSOR)) continue;
                String name = m.getName();
                if (HANDLER.matcher(name).find() || GAME_METHOD.matcher(name).matches()) return c.getSimpleName() + "." + name + " from " + mixin;
            }
            return "";
        } catch (Throwable t) {
            return "the merged methods of " + c.getSimpleName() + " could not be read: " + t;
        }
    }

    /** captureFrameIfNeeded's Screenshot.takeScreenshot call: start the asynchronous read and hand FancyMenu the marker
     *  (the mixin first finishes a capture whose marker FancyMenu still holds). */
    public static NativeImage capture(RenderTarget target, Operation<NativeImage> original) {
        if (!enabled || !RenderSystem.isOnRenderThread() || !supported()) {
            stockCaptures++;
            return original.call(target);
        }
        if (!foreignHook().isEmpty()) {
            stoodAside++;
            stockCaptures++;
            return original.call(target);
        }
        int width = target.f_83915_, height = target.f_83916_;
        Pending p = new Pending(width, height);
        int previousPack = GlStateManager._getInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING);
        if (buffer < 0) buffer = GlStateManager._glGenBuffers();
        GlStateManager._glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, buffer);
        if (bufferSize != p.size) {
            GL15.glBufferData(GL21.GL_PIXEL_PACK_BUFFER, p.size, GL15.GL_STREAM_READ);
            bufferSize = p.size;
        }
        // exactly what Screenshot.takeScreenshot does before its read: bind the colour texture, the RGBA pack alignment
        RenderSystem.bindTexture(target.m_83975_());
        NativeImage.Format.RGBA.m_85166_();
        GL11.nglGetTexImage(GL11.GL_TEXTURE_2D, 0, NativeImage.Format.RGBA.m_85170_(), GL11.GL_UNSIGNED_BYTE, 0L);
        GlStateManager._glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, previousPack);
        p.fence = GL32.glFenceSync(GL32.GL_SYNC_GPU_COMMANDS_COMPLETE, 0);
        if (SHADOW) p.shadow = original.call(target);
        pending = p;
        started++;
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: fancymenu_seamless_capture_async applies (FancyMenu's seamless-loading captures are read back without a frame stall)");
        }
        return marker();
    }

    /** Called while FancyMenu holds the marker (its next captures' one-per-second check): the finished image, or null. */
    public static NativeImage poll() {
        Pending p = pending;
        if (p == null || !RenderSystem.isOnRenderThread()) return null;
        if (p.result == null && !p.copying) {
            int status = GL32.glClientWaitSync(p.fence, 0, 0L);
            if (status == GL32.GL_TIMEOUT_EXPIRED) return null;
            if (status == GL32.GL_WAIT_FAILED) return finishNow();
            GL32.glDeleteSync(p.fence);
            p.fence = 0L;
            map(p);
            p.copying = true;
            Util.m_183991_().execute(() -> p.result = convert(p));
            return null;
        }
        if (p.result == null) return null;
        return publish(p);
    }

    /** saveAndClearCapture while FancyMenu holds the marker: the capture is finished now (render thread). */
    public static NativeImage finishNow() {
        Pending p = pending;
        if (p == null) return null;
        if (!RenderSystem.isOnRenderThread()) {
            LOGGER.warn("Bons and Furious: fancymenu_seamless_capture_async: a pending capture was saved off the render thread and is dropped");
            pending = null;
            return null;
        }
        if (p.result == null && !p.copying) {
            if (p.fence != 0L) {
                while (GL32.glClientWaitSync(p.fence, GL32.GL_SYNC_FLUSH_COMMANDS_BIT, 1_000_000_000L) == GL32.GL_TIMEOUT_EXPIRED) {
                    Thread.onSpinWait();
                }
                GL32.glDeleteSync(p.fence);
                p.fence = 0L;
            }
            map(p);
            p.copying = true;
            p.result = convert(p);
        }
        while (p.result == null) Thread.onSpinWait();     // a background copy that is still running
        finishedNow++;
        return publish(p);
    }

    /** clearLastCapturedImage while FancyMenu holds the marker: the pending capture is dropped (render thread). */
    public static void drop() {
        Pending p = pending;
        if (p == null) return;
        pending = null;
        dropped++;
        if (!RenderSystem.isOnRenderThread()) return;
        if (p.fence != 0L) GL32.glDeleteSync(p.fence);
        if (p.copying) {
            while (p.result == null) Thread.onSpinWait();
        }
        unmap(p);
        if (p.result != null) p.result.close();
        if (p.shadow != null) p.shadow.close();
    }

    private static void map(Pending p) {
        int previousPack = GlStateManager._getInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING);
        GlStateManager._glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, buffer);
        p.mapped = GL30.nglMapBufferRange(GL21.GL_PIXEL_PACK_BUFFER, 0L, p.size, GL30.GL_MAP_READ_BIT);
        GlStateManager._glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, previousPack);
    }

    private static void unmap(Pending p) {
        if (p.mapped == 0L) return;
        int previousPack = GlStateManager._getInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING);
        GlStateManager._glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, buffer);
        GL15.glUnmapBuffer(GL21.GL_PIXEL_PACK_BUFFER);
        GlStateManager._glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, previousPack);
        p.mapped = 0L;
    }

    /** The mapped bytes into a new image, then the alpha fill and row flip Screenshot.takeScreenshot applies (any thread). */
    private static NativeImage convert(Pending p) {
        NativeImage image = new NativeImage(p.width, p.height, false);
        long dst = ((NativeImagePixels) (Object) image).bons$fancymenuCaptureC49Pixels();
        if (p.mapped != 0L) MemoryUtil.memCopy(p.mapped, dst, p.size);
        for (long a = dst, end = dst + p.size; a < end; a += 4) MemoryUtil.memPutInt(a, MemoryUtil.memGetInt(a) | 0xFF000000);
        image.m_85122_();
        return image;
    }

    private static NativeImage publish(Pending p) {
        unmap(p);
        pending = null;
        published++;
        NativeImage image = p.result;
        if (p.shadow != null) {
            SHADOW_CHECKS.incrementAndGet();
            if (!same(image, p.shadow) && SHADOW_MISMATCHES.incrementAndGet() <= 20) {
                LOGGER.warn("Bons and Furious: fancymenu_seamless_capture_async shadow mismatch: the asynchronous capture differs from FancyMenu's own");
            }
            p.shadow.close();
        }
        return image;
    }

    private static boolean same(NativeImage a, NativeImage b) {
        if (a.m_84982_() != b.m_84982_() || a.m_85084_() != b.m_85084_()) return false;
        long pa = ((NativeImagePixels) (Object) a).bons$fancymenuCaptureC49Pixels(), pb = ((NativeImagePixels) (Object) b).bons$fancymenuCaptureC49Pixels();
        long n = (long) a.m_84982_() * a.m_85084_() * 4L;
        for (long i = 0; i < n; i += 8) {
            if (n - i >= 8) {
                if (MemoryUtil.memGetLong(pa + i) != MemoryUtil.memGetLong(pb + i)) return false;
            } else {
                for (long j = i; j < n; j++) if (MemoryUtil.memGetByte(pa + j) != MemoryUtil.memGetByte(pb + j)) return false;
            }
        }
        return true;
    }

    /** Harness only: the bytes of an image. */
    public static byte[] bytes(NativeImage image) {
        long ptr = ((NativeImagePixels) (Object) image).bons$fancymenuCaptureC49Pixels();
        byte[] out = new byte[image.m_84982_() * image.m_85084_() * 4];
        for (int i = 0; i < out.length; i++) out[i] = MemoryUtil.memGetByte(ptr + i);
        return out;
    }

    /** Harness only. */
    public static boolean pending() {
        return pending != null;
    }

    /** The pixel address of a NativeImage (implemented on NativeImage by this group's NativeImagePixelsMixin). */
    public interface NativeImagePixels {
        long bons$fancymenuCaptureC49Pixels();
    }
}
