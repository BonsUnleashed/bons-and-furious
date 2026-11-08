package bons.furious.patch.xaeroworldmap_gpu_c49;

import java.nio.IntBuffer;
import org.apache.logging.log4j.LogManager;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL33;
import org.lwjgl.opengl.GLCapabilities;
import xaero.map.graphics.TextureUploadBenchmark;

/**
 * Bons and Furious switch xaeroworldmap_upload_benchmark_async (Xaero's World Map 1.47.0 for NeoForge 1.21.1, tested build;
 * client; since 1.0.49). Kind: change (trade-off, off by default). Idea: Collections Of Optimizations
 * (xaeroworldmap.skipUploadBenchmark), idea text only. Xaero's World Map is All Rights Reserved: none of its code is carried.
 *
 * What World Map does. For the first 512 / 512 / 512 / 256 / 256 / 256 / 256 tile uploads of each kind after the game
 * starts it times every upload: TextureUploader.uploadTextures calls glFinish before the first timed upload of a batch, and
 * TextureUploadBenchmark.post calls glFinish after each one and adds the wall time since pre() to that kind's total. Each
 * glFinish makes the render thread wait until the GPU has finished everything queued so far, the frame's own drawing
 * included: up to 2,560 such waits while the map fills for the first time. The averages are then used for the rest of the
 * session as the time each upload is expected to take (at most 1, 1, 3, 4, 1 or 1 ms by kind), which sets how many tiles
 * upload per frame.
 *
 * What the switch changes (WmUploadTimerMixin, WmUploadFinishMixin). No glFinish: pre() writes a GPU timestamp query, post()
 * writes a second one and returns; once both results are available (a frame or two later, checked without waiting at the
 * start of the next upload pass), post() runs as shipped with the GPU time between the two timestamps as the measured time
 * and without its glFinish. Results that arrive after a kind's benchmark is complete are dropped, so the averages use the
 * same number of samples. Without timer queries (no GL 3.3 or ARB_timer_query) everything runs as shipped.
 * Trade-off: the averages are the GPU time of the uploads instead of a wall-clock measurement around glFinish (which also
 * counts the driver's work on the render thread and the wait itself), so after the benchmark the map can upload more tiles
 * per frame than without the switch, still at most the mod's per-kind defaults and within its frame budget.
 *
 * -Dbons_and_furious.xaeroworldmapUploadBenchmarkAsync=false times as shipped again (emergency off).
 */
public final class UploadTimer {
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.xaeroworldmapUploadBenchmarkAsync", "true"));
    /** Render thread only. timed = uploads timed with queries, replayed = results handed to post(), dropped = results after
     *  their kind's benchmark was complete, finishes = glFinish calls left out. */
    public static long timed, replayed, dropped, finishes;

    private static int usable;               // 0 = not checked yet, 1 = timer queries present, 2 = absent
    private static boolean announced;
    private static boolean replaying;
    private static int replayNanos;
    private static int startQuery;           // written by the last pre()
    private static int[] free = new int[0];
    private static int freeCount;
    private static int[] pendStart = new int[64], pendEnd = new int[64], pendType = new int[64];
    private static TextureUploadBenchmark[] pendBench = new TextureUploadBenchmark[64];
    private static int head, size;
    private static final IntBuffer GEN = BufferUtils.createIntBuffer(64);

    private UploadTimer() {
    }

    /** True when the switch applies on this context (render thread). */
    public static boolean active() {
        if (!enabled) return false;
        if (usable == 0) {
            boolean ok;
            try {
                GLCapabilities caps = GL.getCapabilities();
                ok = caps.glQueryCounter != 0L && caps.glGetQueryObjecti64v != 0L && caps.glGetQueryObjectiv != 0L && caps.glGenQueries != 0L;
            } catch (RuntimeException e) {
                ok = false;
            }
            usable = ok ? 1 : 2;
        }
        return usable == 1;
    }

    public static boolean replaying() {
        return replaying;
    }

    public static int replayNanos() {
        return replayNanos;
    }

    private static int query() {
        if (freeCount == 0) {
            GEN.clear();
            GL15.glGenQueries(GEN);
            if (free.length < 64) free = new int[64];
            for (int i = 0; i < 64; i++) free[freeCount++] = GEN.get(i);
        }
        return free[--freeCount];
    }

    private static void recycle(int q) {
        if (freeCount == free.length) free = java.util.Arrays.copyOf(free, free.length * 2);
        free[freeCount++] = q;
    }

    /** TextureUploadBenchmark.pre() with the switch on: the first timestamp instead of the CPU timer. */
    public static void start() {
        startQuery = query();
        GL33.glQueryCounter(startQuery, GL33.GL_TIMESTAMP);
    }

    /** TextureUploadBenchmark.post(type) with the switch on: the second timestamp; the result is read later. */
    public static void end(TextureUploadBenchmark bench, int type) {
        if (startQuery == 0) return;    // no pre() since the last post(): nothing to time
        int endQuery = query();
        GL33.glQueryCounter(endQuery, GL33.GL_TIMESTAMP);
        if (size == pendEnd.length) grow();
        int at = (head + size) % pendEnd.length;
        pendStart[at] = startQuery;
        pendEnd[at] = endQuery;
        pendType[at] = type;
        pendBench[at] = bench;
        size++;
        startQuery = 0;
        timed++;
        if (!announced) announce();
    }

    private static void grow() {
        int n = pendEnd.length;
        int[] s = new int[n * 2], e = new int[n * 2], t = new int[n * 2];
        TextureUploadBenchmark[] b = new TextureUploadBenchmark[n * 2];
        for (int i = 0; i < size; i++) {
            int from = (head + i) % n;
            s[i] = pendStart[from];
            e[i] = pendEnd[from];
            t[i] = pendType[from];
            b[i] = pendBench[from];
        }
        pendStart = s;
        pendEnd = e;
        pendType = t;
        pendBench = b;
        head = 0;
    }

    /** Start of TextureUploader.uploadTextures with the switch on: hand every available result to post(), oldest first,
     *  without waiting for the others. */
    public static void poll() {
        while (size > 0) {
            int at = head;
            if (GL15.glGetQueryObjecti(pendEnd[at], GL15.GL_QUERY_RESULT_AVAILABLE) == 0) return;
            long nanos = GL33.glGetQueryObjecti64(pendEnd[at], GL15.GL_QUERY_RESULT) - GL33.glGetQueryObjecti64(pendStart[at], GL15.GL_QUERY_RESULT);
            TextureUploadBenchmark bench = pendBench[at];
            int type = pendType[at];
            recycle(pendStart[at]);
            recycle(pendEnd[at]);
            pendBench[at] = null;
            head = (head + 1) % pendEnd.length;
            size--;
            if (bench.isFinished(type)) {
                dropped++;
                continue;
            }
            replayNanos = (int) Math.max(0L, Math.min(Integer.MAX_VALUE, nanos));
            replaying = true;
            try {
                bench.post(type);
            } finally {
                replaying = false;
            }
            replayed++;
        }
    }

    private static void announce() {
        announced = true;
        LogManager.getLogger("Bons and Furious").info("Bons and Furious: xaeroworldmap_upload_benchmark_async applies (World Map times its first tile uploads with GPU timestamp queries instead of glFinish)");
    }
}
