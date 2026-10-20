package bons.furious.patch.punchy;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import net.minecraft.server.packs.resources.ResourceManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import punchy.client.model.ResourcePackGeoRegistry;

/**
 * Bons and Furious switch punchy_lazy_resource_scans (Punchy 2.8g for Minecraft 1.20.1, client; our pack does not install
 * Punchy, the switch is for other packs, proven offline on the real classes).
 *
 * <p>On every resource reload (twice per launch, then on every pack change or F3+T), Punchy's ResourcePackGeoRegistry.load
 * lists every "geo/*.geo.json" and every "models/*.json" of every resource pack and JSON-parses each one into two maps
 * (GEO_MODELS, MODEL_BONES), on the game thread (a ResourceManagerReloadListener's apply step): in a large pack about a
 * hundred thousand model files. Only getGeoModel and getBonesForTarget read those maps, and in Punchy 2.8g nothing calls
 * them (getGeoModel's one caller is getBonesForTarget, which has none).
 *
 * <p>With the switch on, load() still clears all five maps and builds the target mappings, but the two scans wait: the
 * first call of either reader runs Punchy's own load(manager) once more with the same resource manager before reading, which
 * clears and rebuilds all five maps exactly as the reload built them (the mappings come out equal: same manager, same
 * files). A new load() drops a wait that never ran, as stock drops the old maps. So every value a reader can see is the
 * value stock gives it, with one documented exception: a reader running during the NEXT reload after the resource manager
 * was closed and before Punchy's listener reloads would find the maps empty (stock: the previous maps). To keep even that
 * window closed, the switch only waits when no class of Punchy's own jar calls the readers (a constant-pool scan of the
 * jar, once per session); Punchy 2.8g has none, so there the scans never run at all.
 *
 * <p>-Dbons_and_furious.punchyLazyResourceScans=false turns it off at runtime. SHADOW MODE for rigs:
 * -Dbons_and_furious.punchyLazyResourceScans.shadow=true runs the scans as stock and counts SHADOW_CHECKS (scans the
 * switch would have skipped) and SHADOW_MISMATCHES (reader calls, each of which would have had to run the scans; expected
 * 0 with Punchy 2.8g; WARN for the first 20).
 */
public final class LazyGeoScans {
    /** Runtime switch (the config switch acts when classes are transformed). */
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.punchyLazyResourceScans", "true"));
    /** Shadow mode (see the class comment). */
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.punchyLazyResourceScans.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    /** Scans skipped and late runs (statistics for probes). */
    public static long deferred, ranLate;
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    private static final String OWNER = "punchy/client/model/ResourcePackGeoRegistry";
    private static volatile ResourceManager pending;
    private static boolean running;
    private static volatile int readerCallers = -1;   // -1 not scanned yet, 0 none, > 0 classes of Punchy calling the readers
    private static volatile boolean announced, warned;

    private LazyGeoScans() {
    }

    /** HEAD of ResourcePackGeoRegistry.load: a wait that never ran is dropped (stock clears the maps it would have filled). */
    public static void onLoad() {
        if (pending != null) {
            synchronized (LazyGeoScans.class) {
                if (!running) pending = null;
            }
        }
    }

    /** HEAD of loadGeoModels / loadModelBones: true = skip the scan now; the first reader runs it. */
    public static boolean defer(ResourceManager manager) {
        if (!enabled || manager == null) return false;
        synchronized (LazyGeoScans.class) {
            if (running) return false;                         // the late run itself
            if (!noReadersInPunchy()) return false;
            if (SHADOW) {
                SHADOW_CHECKS.incrementAndGet();
                return false;
            }
            pending = manager;
            deferred++;
        }
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: punchy_lazy_resource_scans: Punchy's geo and model scans wait for their first reader (no Punchy class reads them)");
        }
        return true;
    }

    /** HEAD of getGeoModel / getBonesForTarget: run the waiting scans first, with the same resource manager. */
    public static void beforeRead() {
        if (SHADOW) {
            long n = SHADOW_MISMATCHES.incrementAndGet();
            if (n <= 20) LOGGER.warn("Bons and Furious: punchy_lazy_resource_scans SHADOW MISMATCH {}: Punchy's geo registry was read", n);
            return;
        }
        if (pending == null) return;
        synchronized (LazyGeoScans.class) {
            ResourceManager m = pending;
            if (m == null || running) return;
            pending = null;
            running = true;
            try {
                ResourcePackGeoRegistry.load(m);              // clears and rebuilds all five maps from the same manager
                ranLate++;
            } catch (Throwable t) {
                if (!warned) {
                    warned = true;
                    LOGGER.warn("Bons and Furious: punchy_lazy_resource_scans: the waiting scans failed ({}); Punchy's geo maps stay as they are", t.toString());
                }
            } finally {
                running = false;
            }
        }
    }

    /** True when no class of Punchy's jar other than ResourcePackGeoRegistry calls getGeoModel or getBonesForTarget. */
    static boolean noReadersInPunchy() {
        int c = readerCallers;
        if (c < 0) {
            c = countReaderCallers();
            readerCallers = c;
            if (c != 0) LOGGER.warn("Bons and Furious: punchy_lazy_resource_scans steps aside: {} Punchy's geo registry readers are called by this Punchy build",
                    c == Integer.MAX_VALUE ? "cannot check whether" : c + " class(es) say");
        }
        return c == 0;
    }

    private static int countReaderCallers() {
        Path jar = punchyJar();
        if (jar == null) return Integer.MAX_VALUE;
        int callers = 0;
        try (ZipFile z = new ZipFile(jar.toFile())) {
            for (Enumeration<? extends ZipEntry> en = z.entries(); en.hasMoreElements(); ) {
                ZipEntry e = en.nextElement();
                String n = e.getName();
                if (!n.endsWith(".class") || n.equals(OWNER + ".class") || n.startsWith("META-INF/")) continue;
                try (InputStream in = z.getInputStream(e)) {
                    if (callsReaders(in.readAllBytes())) callers++;
                }
            }
        } catch (Exception ex) {
            return Integer.MAX_VALUE;
        }
        return callers;
    }

    /** Punchy's mod file through FML, or (offline) the jar the class was loaded from. */
    private static Path punchyJar() {
        try {
            Object list = Class.forName("net.minecraftforge.fml.ModList").getMethod("get").invoke(null);
            if (list != null) {
                Object info = list.getClass().getMethod("getModFileById", String.class).invoke(list, "punchy");
                if (info != null) {
                    Object file = info.getClass().getMethod("getFile").invoke(info);
                    return (Path) file.getClass().getMethod("getFilePath").invoke(file);
                }
            }
        } catch (Throwable ignored) {
            // not running under FML
        }
        try {
            java.net.URL url = ResourcePackGeoRegistry.class.getProtectionDomain().getCodeSource().getLocation();
            Path p = Path.of(url.toURI());
            if (p.toString().endsWith(".jar")) return p;
        } catch (Throwable ignored) {
            // no code source
        }
        // outside FML (offline tools): the class path jar that holds Punchy's registry class
        for (String entry : System.getProperty("java.class.path", "").split(java.io.File.pathSeparator)) {
            if (!entry.endsWith(".jar")) continue;
            try (ZipFile z = new ZipFile(entry)) {
                if (z.getEntry(OWNER + ".class") != null) return Path.of(entry);
            } catch (Exception ignored) {
                // not a readable jar
            }
        }
        return null;
    }

    /** Constant-pool scan: does this class name OWNER.getGeoModel or OWNER.getBonesForTarget as a method reference? */
    static boolean callsReaders(byte[] b) {
        if (b.length < 10 || (b[0] & 0xff) != 0xca || (b[1] & 0xff) != 0xfe) return false;
        int n = u2(b, 8);
        String[] utf = new String[n];
        int[] classIdx = new int[n], refClass = new int[n], refNat = new int[n], natName = new int[n];
        boolean[] isRef = new boolean[n];
        int off = 10;
        for (int i = 1; i < n; i++) {
            int tag = b[off] & 0xff;
            switch (tag) {
                case 1 -> {
                    int len = u2(b, off + 1);
                    utf[i] = new String(b, off + 3, len, java.nio.charset.StandardCharsets.UTF_8);
                    off += 3 + len;
                }
                case 3, 4 -> off += 5;
                case 5, 6 -> {
                    off += 9;
                    i++;
                }
                case 7 -> {
                    classIdx[i] = u2(b, off + 1);
                    off += 3;
                }
                case 8, 16, 19, 20 -> off += 3;
                case 9 -> off += 5;
                case 10, 11 -> {
                    isRef[i] = true;
                    refClass[i] = u2(b, off + 1);
                    refNat[i] = u2(b, off + 3);
                    off += 5;
                }
                case 12 -> {
                    natName[i] = u2(b, off + 1);
                    off += 5;
                }
                case 15 -> off += 4;
                case 17, 18 -> off += 5;
                default -> {
                    return true;   // unknown layout: assume the worst
                }
            }
        }
        for (int i = 1; i < n; i++) {
            if (!isRef[i]) continue;
            String owner = utf[classIdx[refClass[i]]];
            String name = utf[natName[refNat[i]]];
            if (OWNER.equals(owner) && ("getGeoModel".equals(name) || "getBonesForTarget".equals(name))) return true;
        }
        return false;
    }

    private static int u2(byte[] b, int off) {
        return (b[off] & 0xff) << 8 | (b[off + 1] & 0xff);
    }
}
