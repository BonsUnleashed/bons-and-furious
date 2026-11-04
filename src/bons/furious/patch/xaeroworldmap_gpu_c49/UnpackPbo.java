package bons.furious.patch.xaeroworldmap_gpu_c49;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.nio.ByteBuffer;
import org.apache.logging.log4j.LogManager;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GLCapabilities;
import xaero.map.pool.buffer.PoolTextureDirectBufferUnit;

/**
 * Bons and Furious switch xaeroworldmap_unpack_pbo_orphan (Xaero's World Map 1.47.0, tested build; COO's pinned 1.44.2 has
 * the same method; client; since 1.0.49). Kind: exact variant. Idea: Collections Of Optimizations
 * (xaeroworldmap.orphanUploadPbo), idea text only. Xaero's World Map is All Rights Reserved: none of its code is carried.
 *
 * What World Map does. RegionTexture.writeToUnpackPBO maps a tile's 16,384-byte pixel unpack buffer with
 * glMapBuffer(GL_PIXEL_UNPACK_BUFFER, GL_WRITE_ONLY) (through its PixelBuffers, which always uses the core GL 1.5 call),
 * copies the tile's pixels in, unmaps, and queues the texture upload that reads the buffer. LWJGL's two-argument glMapBuffer
 * also asks the driver for the buffer's size (glGetBufferParameteri) on every map. When the buffer's previous upload is
 * still queued on the GPU, a WRITE_ONLY map does not tell the driver that the old contents may be dropped, so the driver
 * may wait for that upload before it returns.
 *
 * What the switch does (WmUnpackPboMixin: an @Inject at the method's head notes the tile's source buffer, a MixinExtras
 * @WrapOperation on that map call reads it; both run on the render thread, the only one World Map uploads on, and the
 * retry path's second call notes the same buffer again). When the tile's pixels are
 * exactly the buffer's 16,384 bytes, the buffer is mapped with glMapBufferRange(GL_PIXEL_UNPACK_BUFFER, 0, 16384,
 * GL_MAP_WRITE_BIT | GL_MAP_INVALIDATE_BUFFER_BIT): the driver may give the buffer fresh memory instead of waiting, and no
 * size query is made. Every byte the upload reads is written before it is read, so every texture gets the same pixels.
 * Shorter writes (compressed tiles keep the old tail of the buffer), a context without glMapBufferRange and any other map
 * call run as shipped; a failed map returns null into World Map's own recovery path, as before.
 *
 * -Dbons_and_furious.xaeroworldmapUnpackPboOrphan=false maps as shipped again (emergency off).
 */
public final class UnpackPbo {
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.xaeroworldmapUnpackPboOrphan", "true"));
    /** Render thread only: maps made with the invalidating range map, maps left to the shipped call. */
    public static long ranged, shipped;
    private static int usable;            // 0 = not checked yet, 1 = glMapBufferRange present, 2 = absent
    private static boolean announced;

    private UnpackPbo() {
    }

    private static boolean usable() {
        if (usable == 0) {
            boolean ok;
            try {
                GLCapabilities caps = GL.getCapabilities();
                ok = caps.glMapBufferRange != 0L;
            } catch (RuntimeException e) {
                ok = false;
            }
            usable = ok ? 1 : 2;
        }
        return usable == 1;
    }

    /** Head of writeToUnpackPBO (render thread): the tile's source buffer, read by the map call right after. */
    private static PoolTextureDirectBufferUnit source;

    public static void source(PoolTextureDirectBufferUnit buffer) {
        source = buffer;
    }

    /** The map call of writeToUnpackPBO (target GL_PIXEL_UNPACK_BUFFER, access GL_WRITE_ONLY in 1.47.0). */
    public static ByteBuffer map(int target, int access, Operation<ByteBuffer> original) {
        PoolTextureDirectBufferUnit buffer = source;
        source = null;
        if (enabled && target == 0x88EC && access == 0x88B9 && buffer != null) {
            ByteBuffer src = buffer.getDirectBuffer();
            if (src != null && src.remaining() == 16384 && usable()) {
                ranged++;
                if (!announced) announce();
                return GL30.glMapBufferRange(0x88EC, 0L, 16384L, GL30.GL_MAP_WRITE_BIT | GL30.GL_MAP_INVALIDATE_BUFFER_BIT);
            }
        }
        shipped++;
        return original.call(target, access);
    }

    private static void announce() {
        announced = true;
        LogManager.getLogger("Bons and Furious").info("Bons and Furious: xaeroworldmap_unpack_pbo_orphan applies (World Map tile buffers are mapped with an invalidating range map)");
    }
}
