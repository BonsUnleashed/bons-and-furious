package bons.furious.patch.embeddium_draw;

import java.io.IOException;
import java.io.InputStream;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.embeddedt.embeddium.impl.gl.device.MultiDrawBatch;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch embeddium_merged_draws (tested build: Embeddium 1.0.15+mc1.21.1 on Minecraft 1.21.1 /
 * NeoForge 21.1.252; client only; first written for Embeddium 0.3.31+mc1.20.1). Helper of
 * bons.furious.mixin.embeddium_draw.DefaultChunkRendererMergeMixin. PENDING (1.0.30 assembler's decision): ships only if
 * the client rig's FPS A/B shows the driver/GPU-side gain.
 *
 * Ported to 1.21.1: names only. Embeddium 1.0.15's fillCommandBuffer/addDrawCommands, SectionRenderDataStorage.setMeshes,
 * MultiDrawBatch and SharedQuadIndexBuffer (index pattern 4q + {0,1,2,2,3,0}) decompile identical to 0.3.31 after the
 * package move; its chunk shaders (assets/embeddium/shaders/blocks/block_layer_opaque.vsh/.fsh and the four includes)
 * mention none of gl_PrimitiveID, gl_DrawID, gl_BaseVertex (resource scan of the 1.0.15 jar). Oculus has no 1.21.1
 * build and Iris 1.8.12 declares Embeddium incompatible (its neoforge.mods.toml), so net.irisshaders.iris.Iris is never
 * on the class path next to Embeddium and the shader-pack check below takes its "no shader mod" branch; it is kept
 * unchanged so a future shader mod with the same holder is still checked rather than assumed safe.
 *
 * Embeddium draws a region's terrain with one glMultiDrawElementsBaseVertex call whose command list has one entry per
 * visible mesh facing of every section (fillCommandBuffer/addDrawCommands; Embeddium 0.3.31 never merges entries). A
 * section's facings are stored back to back in the region's vertex buffer (SectionRenderDataStorage.setMeshes), so with
 * Oculus's shadow pass (no face culling) and for runs of visible facings in the main view, consecutive commands cover
 * touching vertex ranges. In the unsorted passes every command reads the shared quad index buffer from element 0
 * (index pattern 4q+{0,1,2,2,3,0} per quad q): a command (base b, count c) followed by (base b + c/6*4, count c') draws
 * exactly the vertices of the single command (base b, count c + c'), in the same order, so the same triangles are drawn
 * in the same order with the same gl_VertexID (index + base vertex). This pass merges exactly such neighbours in place;
 * commands that do not touch, a count that is not a whole number of quads, and the sorted (translucent) passes, whose
 * commands read their own index ranges, are left as filled.
 *
 * What a merge can change: per-command shader inputs. gl_PrimitiveID restarts per command, gl_DrawID numbers the
 * commands and gl_BaseVertex is the command's base vertex. Embeddium's and Oculus's own chunk shaders use none of them
 * (resource scan of both jars); a shader pack might, so the merge only runs when no shader pack is active, or when no
 * file of the active pack mentions gl_PrimitiveID, gl_DrawID or gl_BaseVertex (checked once per pack; a pack that
 * cannot be read counts as "mentions"). The shared index buffer grows earlier (larger merged counts; Embeddium's own
 * ensureCapacity call does it), which uses GPU memory but draws nothing else.
 */
public final class MergedDraws {
    /** Runtime switch (the config switch acts when classes are transformed). -Dbons_and_furious.embeddiumMergedDraws=false also turns it off. */
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.embeddiumMergedDraws", "true"));
    /**
     * Rig self-check: -Dbons_and_furious.embeddiumMergedDraws.shadow=true draws the unmerged commands as shipped and
     * checks, for every batch the merge would change, that the merged commands expand to the same vertex sequence.
     */
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.embeddiumMergedDraws.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    /**
     * Rig counters (render thread only, plain fields: read them after the frames of interest): draw commands of the
     * unsorted passes as filled and as drawn, with face culling (main view) and without (Oculus's shadow pass), and the
     * number of batches counted.
     */
    public static long commandsInCulled, commandsOutCulled, commandsInUnculled, commandsOutUnculled, batches;
    /** Set once the merge mixin is in the renderer (its static initializer runs with DefaultChunkRenderer's). */
    public static volatile boolean applied;
    /** Render thread only: the command count of the last batch merge() was given (before merging). */
    public static int lastFilled;

    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    private static final String[] TOKENS = {"gl_PrimitiveID", "gl_DrawID", "gl_BaseVertex"};
    private static final sun.misc.Unsafe U;
    private static final MethodHandle CURRENT_PACK, PACK_NAME, PACKS_DIR;
    private static Object lastPack;
    private static boolean lastPackOk;
    private static volatile boolean announced;

    static {
        sun.misc.Unsafe u = null;
        try {
            Field f = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            u = (sun.misc.Unsafe) f.get(null);
        } catch (Throwable t) {
            enabled = false;
            LOGGER.warn("Bons and Furious: embeddium_merged_draws is inactive: no memory access ({})", t.toString());
        }
        U = u;
        MethodHandle cur = null, name = null, dir = null;
        try {
            Class<?> iris = Class.forName("net.irisshaders.iris.Iris", false, MergedDraws.class.getClassLoader());
            Field current = iris.getDeclaredField("currentPack");
            current.setAccessible(true);
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            cur = lookup.unreflectGetter(current).asType(MethodType.methodType(Object.class));
            Method n = iris.getMethod("getCurrentPackName");
            Method d = iris.getMethod("getShaderpacksDirectory");
            name = lookup.unreflect(n).asType(MethodType.methodType(String.class));
            dir = lookup.unreflect(d).asType(MethodType.methodType(Path.class));
        } catch (ClassNotFoundException e) {
            // no Oculus: Embeddium's own chunk shaders only
        } catch (Throwable t) {
            cur = name = dir = null;
            enabled = false;
            LOGGER.warn("Bons and Furious: embeddium_merged_draws is inactive: Oculus is installed but its shader pack cannot be inspected ({})", t.toString());
        }
        CURRENT_PACK = cur;
        PACK_NAME = name;
        PACKS_DIR = dir;
    }

    private MergedDraws() {
    }

    /** Called from the merge mixin's static initialiser (DefaultChunkRenderer's class initialisation): the wrapper is present. */
    public static boolean markApplied() {
        applied = true;
        return true;
    }

    private static int[] scratchBase = new int[64], scratchCount = new int[64], mergedBase = new int[64], mergedCount = new int[64];
    private static long[] scratchPtr = new long[64], mergedPtr = new long[64];

    /** SHADOW mode (render thread): merge, check the merged commands draw the same triangles, count, restore the unmerged commands. */
    public static void shadowCheck(MultiDrawBatch batch, boolean culling) {
        int n = batch.size;
        if (n > scratchBase.length) {
            int cap = Math.max(n, scratchBase.length * 2);
            scratchBase = new int[cap];
            scratchCount = new int[cap];
            scratchPtr = new long[cap];
            mergedBase = new int[cap];
            mergedCount = new int[cap];
            mergedPtr = new long[cap];
        }
        copyOut(batch, scratchBase, scratchCount, scratchPtr);
        merge(batch);
        int m = copyOut(batch, mergedBase, mergedCount, mergedPtr);
        SHADOW_CHECKS.incrementAndGet();
        if (!sameTriangles(scratchBase, scratchCount, scratchPtr, n, mergedBase, mergedCount, mergedPtr, m)) {
            mismatch(n + " commands merged into " + m + " draw different triangles");
        }
        count(n, m, culling);
        copyIn(batch, scratchBase, scratchCount, scratchPtr, n);
    }

    /** True when the merge runs for a fill of this kind: switch on, unsorted pass, no shader pack input that a merge changes. */
    public static boolean active(boolean sortedPass) {
        return applied && enabled && !sortedPass && packAllowsMerge();
    }

    /** The active shader pack (Oculus's Iris.currentPack) mentions none of gl_PrimitiveID, gl_DrawID, gl_BaseVertex. Render thread. */
    static boolean packAllowsMerge() {
        if (CURRENT_PACK == null) return true;
        Object pack;
        try {
            pack = (Object) CURRENT_PACK.invokeExact();
        } catch (Throwable t) {
            return false;
        }
        if (pack == null) {              // no pack in use: Embeddium's (Oculus's passthrough) chunk shaders
            lastPack = null;             // 1.0.34: do not keep the last pack (sources, texture data) alive once shaders are off
            return true;
        }
        if (pack != lastPack) {
            lastPack = pack;
            lastPackOk = scanPack();
        }
        return lastPackOk;
    }

    private static boolean scanPack() {
        String name = null;
        try {
            name = (String) PACK_NAME.invokeExact();
            Path dir = (Path) PACKS_DIR.invokeExact();
            if (name == null || dir == null) return refuse(name, "pack not located");
            Path root = dir.resolve(name);
            String hit;
            if (Files.isDirectory(root)) hit = scanDirectory(root);
            else if (Files.isRegularFile(root)) hit = scanZip(root);
            else return refuse(name, "pack not found at " + root);
            if (hit != null) return refuse(name, "it uses " + hit);
            LOGGER.info("Bons and Furious: embeddium_merged_draws: shader pack {} uses no per-draw shader input; touching draw commands are merged", name);
            return true;
        } catch (Throwable t) {
            return refuse(name, "could not read it: " + t);
        }
    }

    private static boolean refuse(String name, String why) {
        LOGGER.info("Bons and Furious: embeddium_merged_draws leaves draw commands unmerged with shader pack {} ({})", name, why);
        return false;
    }

    private static boolean shaderFile(String n) {
        String s = n.toLowerCase(Locale.ROOT);
        return s.endsWith(".glsl") || s.endsWith(".vsh") || s.endsWith(".fsh") || s.endsWith(".gsh") || s.endsWith(".tcs") || s.endsWith(".tes")
                || s.endsWith(".csh") || s.endsWith(".vert") || s.endsWith(".frag") || s.endsWith(".geom") || s.endsWith(".inc") || s.endsWith(".h");
    }

    private static String scanDirectory(Path root) throws IOException {
        try (Stream<Path> files = Files.walk(root)) {
            for (Path p : (Iterable<Path>) files::iterator) {
                if (Files.isRegularFile(p) && shaderFile(p.getFileName().toString())) {
                    String hit = token(Files.readAllBytes(p));
                    if (hit != null) return hit + " (" + root.relativize(p) + ")";
                }
            }
        }
        return null;
    }

    private static String scanZip(Path zip) throws IOException {
        try (ZipFile z = new ZipFile(zip.toFile())) {
            Enumeration<? extends ZipEntry> en = z.entries();
            while (en.hasMoreElements()) {
                ZipEntry e = en.nextElement();
                if (e.isDirectory() || !shaderFile(e.getName())) continue;
                try (InputStream in = z.getInputStream(e)) {
                    String hit = token(in.readAllBytes());
                    if (hit != null) return hit + " (" + e.getName() + ")";
                }
            }
        }
        return null;
    }

    private static String token(byte[] data) {
        String text = new String(data, StandardCharsets.ISO_8859_1);
        for (String t : TOKENS) if (text.contains(t)) return t;
        return null;
    }

    /**
     * Merges, in place, each command into the previous one (or the run already merged into it) when both read the shared
     * index buffer from element 0, the run's count is a whole number of quads, the command's base vertex is exactly where
     * the run's vertices end and the merged count still fits in an int. Returns the number of commands before merging and
     * leaves it in lastFilled (render thread; the cache keeps it for the rig's counts on later hits).
     */
    public static int merge(MultiDrawBatch batch) {
        int n = batch.size;
        lastFilled = n;
        if (n < 2) return n;
        long pb = batch.pBaseVertex, pc = batch.pElementCount, pp = batch.pElementPointer;
        int base = U.getInt(pb), count = U.getInt(pc);
        long ptr = U.getLong(pp);
        int out = 0;
        for (int i = 1; i < n; i++) {
            int b = U.getInt(pb + ((long) i << 2)), c = U.getInt(pc + ((long) i << 2));
            long p = U.getLong(pp + ((long) i << 3));
            if (ptr == 0L && p == 0L && count >= 0 && c >= 0 && count % 6 == 0 && (long) base + (long) (count / 6) * 4L == (long) b
                    && (long) count + (long) c <= Integer.MAX_VALUE) {
                count += c;
            } else {
                U.putInt(pb + ((long) out << 2), base);
                U.putInt(pc + ((long) out << 2), count);
                U.putLong(pp + ((long) out << 3), ptr);
                out++;
                base = b;
                count = c;
                ptr = p;
            }
        }
        U.putInt(pb + ((long) out << 2), base);
        U.putInt(pc + ((long) out << 2), count);
        U.putLong(pp + ((long) out << 3), ptr);
        batch.size = out + 1;
        return n;
    }

    /** Statistics for the rig: commands as filled / as drawn, by face culling (main view) or not (Oculus's shadow pass). */
    public static void count(int before, int after, boolean culling) {
        batches++;
        if (culling) {
            commandsInCulled += before;
            commandsOutCulled += after;
        } else {
            commandsInUnculled += before;
            commandsOutUnculled += after;
        }
        if (!announced) {
            announced = true;
            // one INFO line: the shader pack check already announced the merge when a pack is in use
            if (lastPack == null || SHADOW) {
                LOGGER.info("Bons and Furious: embeddium_merged_draws: draw commands whose vertex ranges touch are merged{}",
                        SHADOW ? " (SHADOW mode: the unmerged commands are drawn; the merge is only checked)" : "");
            }
        }
    }

    /**
     * The triangle sequence two command lists draw, compared item by item: a command reading the shared quad index
     * buffer (pointer 0) contributes one item per triangle t (count / 3 of them; the quad's first vertex
     * base + 4 * (t / 2) and which half of the quad, t % 2: exactly the three vertices it reads); any other command
     * contributes itself (base, count, pointer). True when both lists give the same items in the same order.
     */
    public static boolean sameTriangles(int[] baseA, int[] countA, long[] ptrA, int nA, int[] baseB, int[] countB, long[] ptrB, int nB) {
        Cursor a = new Cursor(baseA, countA, ptrA, nA), b = new Cursor(baseB, countB, ptrB, nB);
        while (true) {
            boolean ha = a.next(), hb = b.next();
            if (ha != hb) return false;
            if (!ha) return true;
            if (a.kind != b.kind || a.value != b.value || a.extra != b.extra) return false;
        }
    }

    /** Streams the items of sameTriangles. */
    static final class Cursor {
        final int[] base, count;
        final long[] ptr;
        final int n;
        int i = -1, t, triangles;
        int kind;
        long value, extra;

        Cursor(int[] base, int[] count, long[] ptr, int n) {
            this.base = base;
            this.count = count;
            this.ptr = ptr;
            this.n = n;
        }

        boolean next() {
            while (true) {
                if (this.i >= 0 && this.ptr[this.i] == 0L && this.t < this.triangles) {
                    this.kind = 0;
                    this.value = (long) this.base[this.i] + 4L * (this.t >> 1);
                    this.extra = this.t & 1;
                    this.t++;
                    return true;
                }
                if (++this.i >= this.n) return false;
                this.t = 0;
                this.triangles = Math.max(0, this.count[this.i]) / 3;
                if (this.ptr[this.i] != 0L) {
                    this.kind = 1;
                    this.value = ((long) this.base[this.i] << 32) | (this.count[this.i] & 0xFFFFFFFFL);
                    this.extra = this.ptr[this.i];
                    return true;
                }
            }
        }
    }

    public static void mismatch(String what) {
        if (SHADOW_MISMATCHES.incrementAndGet() <= 20) LOGGER.warn("Bons and Furious: embeddium_merged_draws SHADOW mismatch: {}", what);
    }

    /** Reads the batch's commands [0, size) into plain arrays (SHADOW mode and tests). */
    public static int copyOut(MultiDrawBatch batch, int[] base, int[] count, long[] ptr) {
        int n = batch.size;
        for (int i = 0; i < n; i++) {
            base[i] = U.getInt(batch.pBaseVertex + ((long) i << 2));
            count[i] = U.getInt(batch.pElementCount + ((long) i << 2));
            ptr[i] = U.getLong(batch.pElementPointer + ((long) i << 3));
        }
        return n;
    }

    /** Writes plain arrays back into the batch's commands [0, n) and sets its size (SHADOW mode). */
    public static void copyIn(MultiDrawBatch batch, int[] base, int[] count, long[] ptr, int n) {
        for (int i = 0; i < n; i++) {
            U.putInt(batch.pBaseVertex + ((long) i << 2), base[i]);
            U.putInt(batch.pElementCount + ((long) i << 2), count[i]);
            U.putLong(batch.pElementPointer + ((long) i << 3), ptr[i]);
        }
        batch.size = n;
    }
}
