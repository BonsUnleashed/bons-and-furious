package bons.furious.patch.endertech_start_reads;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.logging.LogUtils;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;

/**
 * Bons and Furious switch forgeendertech_start_config_reads_once (ForgeEndertech 12.1.4.0 for NeoForge 1.21.1, the library
 * of AdLods and the other EnderTech mods; ported from the Forge switch for ForgeEndertech 11.1.6.0: Mojang member names,
 * the logic is unchanged).
 *
 * ForgeEndertech rebuilds its biome wind table on every server level load (GameWorld.WorldData.initBiomesWinds): it
 * constructs a UnitConfig for every file of config/forgeendertech/Biomes, and UnitConfig's constructor loads the file
 * twice (Configuration's constructor loads it, then UnitConfig calls load() again). Each load creates the parent
 * folders, asks whether the file exists and can be read, opens it and parses it. With 442 biome files and 34
 * dimensions that is 30,056 loads at every world start for 34 identical tables; AdLods reloads its own files the same
 * way on each level load.
 *
 * While the server is starting (MinecraftServer.isReady() is still false) the bytes of each config file are read from
 * disk once: the first load of a path runs the original code (the stream it opens is read to the end here and handed
 * on from memory), and a later load of the same path answers mkdirs / exists / canRead from that first load and parses
 * the remembered bytes with ForgeEndertech's own parser, so every Configuration object, every value and every random
 * draw of the mod's own code is as before. A path is forgotten when ForgeEndertech saves that file (Configuration.save)
 * or a load of it fails, and everything is forgotten when the server is ready or another server starts.
 * Variant: a config file that something other than ForgeEndertech's own save changes or removes while the server is
 * starting is read again at the next level load after the start (or the next start), not at the next dimension's load.
 *
 * -Dbons_and_furious.endertechStartReads=false keeps every load as shipped; .metrics=true prints a summary at JVM exit;
 * .verify=true (rigs) answers every repeated load from disk as shipped and compares the three answers and the file's
 * bytes with what would have been replayed: a difference is counted and logged as an error.
 *
 * Ported to 1.21.1: Configuration.load (one each of the four wrapped calls) and save, UnitConfig's double load and
 * GameWorld.WorldData.initBiomesWinds (a UnitConfig for every file of the Biomes folder at every server level load,
 * NeoForge's LevelEvent.Load) are unchanged in ForgeEndertech 12.1.4.0; NeoForge's ServerLifecycleHooks. The start
 * window is MinecraftServer.isReady() as on Forge (isRunning() is true for the whole start there too). The counts
 * above were measured on Forge; on 1.21.1 they are not measured.
 */
public final class StartReads {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final boolean ACTIVE = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.endertechStartReads", "true"));
    static final boolean VERIFY = Boolean.getBoolean("bons_and_furious.endertechStartReads.verify");
    static final boolean METRICS = VERIFY || Boolean.getBoolean("bons_and_furious.endertechStartReads.metrics");
    public static final LongAdder FROM_DISK = new LongAdder(), FROM_MEMORY = new LongAdder(), UNTOUCHED = new LongAdder(), FORGOTTEN = new LongAdder(),
            COMPARED = new LongAdder(), DIFFERENT = new LongAdder();

    private static final ThreadLocal<Session> CURRENT = new ThreadLocal<>();
    private static volatile Window window;

    static {
        if (METRICS) {
            Runtime.getRuntime().addShutdownHook(new Thread(() -> System.out.println("Bons and Furious forgeendertech_start_config_reads_once: loads from disk "
                    + FROM_DISK.sum() + ", loads " + (VERIFY ? "that would come from memory " : "from memory ") + FROM_MEMORY.sum()
                    + ", loads outside a server start " + UNTOUCHED.sum() + ", paths forgotten after a save or a failed load " + FORGOTTEN.sum()
                    + (VERIFY ? ", answers and files compared with the disk " + COMPARED.sum() + ", differences " + DIFFERENT.sum() : "")),
                    "Bons and Furious EnderTech reads summary"));
        }
    }

    private StartReads() {
    }

    /** The files read during one server's start. */
    private static final class Window {
        final MinecraftServer server;
        final ConcurrentHashMap<String, byte[]> files = new ConcurrentHashMap<>();

        Window(MinecraftServer server) {
            this.server = server;
        }
    }

    /** One load() call inside the window. */
    private static final class Session {
        final String path;
        final byte[] remembered;
        byte[] read;

        Session(String path, byte[] remembered) {
            this.path = path;
            this.remembered = remembered;
        }
    }

    private static Window current() {
        if (!ACTIVE) return null;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        Window w = window;
        // isReady() (field isReady, set after the first tick). NOT isRunning(), which is true for the whole start: the Forge
        // switch's dev1 to dev3 used that one, and every load counted as outside a start (Forge rig p50g_metrics: 36,030).
        if (server == null || server.isReady()) {
            if (w != null) window = null;           // the server is ready (or gone): forget everything
            return null;
        }
        if (w == null || w.server != server) {
            w = new Window(server);
            window = w;
        }
        return w;
    }

    /** Configuration.load(). */
    public static void load(File file, Operation<Void> original) {
        Window w = file == null ? null : current();
        if (w == null) {
            if (METRICS) UNTOUCHED.increment();
            original.call();
            return;
        }
        String path = file.getAbsolutePath();
        Session s = new Session(path, w.files.get(path));
        Session outer = CURRENT.get();
        CURRENT.set(s);
        try {
            original.call();
            if (s.remembered != null) {
                if (METRICS) FROM_MEMORY.increment();
            } else if (s.read != null) {
                w.files.put(path, s.read);
                if (METRICS) FROM_DISK.increment();
            }
        } catch (Throwable t) {
            if (w.files.remove(path) != null && METRICS) FORGOTTEN.increment();
            throw t;
        } finally {
            CURRENT.set(outer);
        }
    }

    /** True inside a load() that is answered from memory. */
    private static boolean replaying() {
        Session s = CURRENT.get();
        return s != null && s.remembered != null;
    }

    /** With the verify flag: the answer from the disk is given, and compared with the replayed one. */
    private static boolean checked(String what, File file, boolean replayed, boolean real) {
        COMPARED.increment();
        if (replayed != real) {
            DIFFERENT.increment();
            LOGGER.error("Bons and Furious: forgeendertech_start_config_reads_once would have answered {} = {} for {}, the disk says {}", what, replayed, file, real);
        }
        return real;
    }

    /** load(): file.getParentFile().mkdirs(). The folder existed at the first load, so a repeated mkdirs() answers false. */
    public static boolean mkdirs(File folder, Operation<Boolean> original) {
        if (!replaying()) return original.call(folder);
        return VERIFY ? checked("mkdirs", folder, false, original.call(folder)) : false;
    }

    /** load(): file.exists(). */
    public static boolean exists(File file, Operation<Boolean> original) {
        if (!replaying()) return original.call(file);
        return VERIFY ? checked("exists", file, true, original.call(file)) : true;
    }

    /** load(): file.canRead(). */
    public static boolean canRead(File file, Operation<Boolean> original) {
        if (!replaying()) return original.call(file);
        return VERIFY ? checked("canRead", file, true, original.call(file)) : true;
    }

    /** load(): new FileInputStream(file). */
    public static FileInputStream open(File file, Operation<FileInputStream> original) {
        Session s = CURRENT.get();
        if (s == null) return original.call(file);
        byte[] bytes = s.remembered;
        if (bytes == null || VERIFY) {
            byte[] disk;
            try (FileInputStream in = original.call(file)) {      // the original open: the same errors as before
                disk = in.readAllBytes();
            } catch (IOException e) {
                throw sneaky(e);                                  // load() catches IOException around this call
            }
            if (bytes == null) {
                s.read = disk;
            } else {
                COMPARED.increment();
                if (!Arrays.equals(bytes, disk)) {
                    DIFFERENT.increment();
                    LOGGER.error("Bons and Furious: forgeendertech_start_config_reads_once would have replayed {} bytes for {}, the disk holds {} other bytes",
                            bytes.length, file, disk.length);
                }
            }
            bytes = disk;
        }
        return new MemoryFile(bytes);
    }

    /** Configuration.save(): the file on disk may have changed. */
    public static void saved(File file) {
        Window w = window;
        if (w != null && file != null && w.files.remove(file.getAbsolutePath()) != null && METRICS) FORGOTTEN.increment();
    }

    @SuppressWarnings("unchecked")
    private static <T extends Throwable> RuntimeException sneaky(Throwable t) throws T {
        throw (T) t;
    }

    /** A FileInputStream that reads remembered bytes: no file is opened (it sits on an unattached descriptor). */
    private static final class MemoryFile extends FileInputStream {
        private final byte[] bytes;
        private int at;

        MemoryFile(byte[] bytes) {
            super(new FileDescriptor());
            this.bytes = bytes;
        }

        @Override
        public int read() {
            return at < bytes.length ? bytes[at++] & 0xFF : -1;
        }

        @Override
        public int read(byte[] b) {
            return read(b, 0, b.length);
        }

        @Override
        public int read(byte[] b, int off, int len) {
            if (len == 0) return 0;
            if (at >= bytes.length) return -1;
            int n = Math.min(len, bytes.length - at);
            System.arraycopy(bytes, at, b, off, n);
            at += n;
            return n;
        }

        @Override
        public byte[] readAllBytes() {
            byte[] rest = Arrays.copyOfRange(bytes, at, bytes.length);
            at = bytes.length;
            return rest;
        }

        @Override
        public byte[] readNBytes(int len) {
            if (len < 0) throw new IllegalArgumentException("len < 0");
            int n = Math.min(len, bytes.length - at);
            byte[] part = Arrays.copyOfRange(bytes, at, at + n);
            at += n;
            return part;
        }

        @Override
        public long transferTo(OutputStream out) throws IOException {
            int n = bytes.length - at;
            out.write(bytes, at, n);
            at = bytes.length;
            return n;
        }

        @Override
        public long skip(long n) {
            long k = Math.max(0, Math.min(n, bytes.length - at));
            at += (int) k;
            return k;
        }

        @Override
        public int available() {
            return bytes.length - at;
        }

        @Override
        public void close() {
        }
    }
}
