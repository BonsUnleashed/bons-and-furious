package bons.furious.patch.client_class_warmup;

import com.mojang.logging.LogUtils;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.slf4j.Logger;

/**
 * Bons and Furious switch client_class_warmup (Minecraft 1.20.1 on Forge 47.4.28, client). Exact variant.
 *
 * What the game does. Every class used for the first time after joining a world (entity, renderer, model, packet, GUI,
 * particle, block entity and mod event classes) is loaded on the thread that first needs it, usually the render thread:
 * Forge's TransformingClassLoader reads it, every mod's mixins and transformers are applied, and the JVM verifies it.
 * Most of the stutter right after joining is this work.
 *
 * What the switch does.
 * <ul>
 * <li>Recording: from the first title screen a JDK Flight Recorder stream (one event, jdk.ClassDefine, no stack traces)
 *     notes every class the game's class loader defines. The classes defined from joining a world until 3 minutes later
 *     (or until the world is left) are added, in the order they were first defined, to
 *     config/bons_and_furious/warm-classes.txt together with a fingerprint of the mod list (every mod id, version and mod
 *     file name). At most 40,000 entries; the list grows while the mod list stays the same and starts over when it
 *     changes. The stream stops after each window and starts again at the next title screen.</li>
 * <li>Warm-up: on the next launch, when the title screen first opens, one background thread (lowest priority, daemon)
 *     loads each listed class with Class.forName(name, false, gameLoader) - loaded and transformed, never initialised - and
 *     links it (getDeclaredConstructors, which verifies the class) when every constructor parameter type is a JDK,
 *     Minecraft, Mojang or Forge class or a listed class (other classes are only loaded, so no constructor signature asks
 *     for a class the game never loaded). It stops at the end of the list, 10 seconds after the first join, or when the
 *     world is left. Skipped: hidden and lambda classes, Mixin and MixinExtras classes, classes in mixin packages, names
 *     that failed before (kept with a leading '!'), and a list whose fingerprint differs. A class that fails is counted,
 *     marked and never asked for again with that mod list.</li>
 * </ul>
 * The one internal difference (the "Exact variant" label): listed classes are loaded and linked earlier, on another thread;
 * no static initialiser runs early, so the game later does the same work minus the loading. The 1.0.35 server counterpart
 * (vanilla_mob_class_warmup) showed that linking, not loading alone, removes the stall, and that broad mod-class sets log
 * WARN/ERROR lines; this list holds only classes the game itself defined in an earlier session with the same mods.
 *
 * Locks: the render thread (the three hooks) only takes STATE (a few field writes); file reading, merging and writing run
 * on background threads under LIST.
 *
 * -Dbons_and_furious.clientClassWarmup=false switches it off at run time (no recording, no warm-up).
 * -Dbons_and_furious.clientClassWarmup.verify=true (rig runs): at the end of each recorded window one INFO line counts the
 * window's classes defined by the warm-up thread, listed classes the game defined itself (the warm-up came too late or not
 * at all) and new classes (the VERIFY_* counters).
 */
public final class ClassWarmup {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.clientClassWarmup", "true"));
    public static final boolean VERIFY = Boolean.getBoolean("bons_and_furious.clientClassWarmup.verify");
    public static final AtomicLong VERIFY_WARMED_DEFINES = new AtomicLong(), VERIFY_GAME_DEFINES_LISTED = new AtomicLong(),
            VERIFY_GAME_DEFINES_NEW = new AtomicLong();

    public static final String THREAD_NAME = "Bons and Furious class warm-up";
    public static final int MAX_ENTRIES = 40_000;
    public static final long WINDOW_NANOS = TimeUnit.MINUTES.toNanos(3);
    public static final long WARM_AFTER_JOIN_NANOS = TimeUnit.SECONDS.toNanos(10);
    static final String HEADER = "# Bons and Furious client_class_warmup: classes the game defined in the first minutes after joining a world,\n"
            + "# loaded and linked in the background on the next launch (none is initialised early). Written by the game;\n"
            + "# delete this file to start over. A line starting with ! failed to load and is skipped.\n";

    private static final AtomicBoolean WARM_STARTED = new AtomicBoolean(), INFO_DONE = new AtomicBoolean(), WARNED_JFR = new AtomicBoolean();
    private static final Object STATE = new Object(), LIST = new Object();
    private static volatile long firstJoinNanos;
    private static volatile boolean stopWarm;
    /** Guarded by LIST: the list as read at start (fingerprint checked) or as last written, and this launch's fingerprint. */
    private static WarmList list;
    private static String fingerprint;
    /** Names that failed in this launch's warm-up (written with the next list). */
    private static final Set<String> FAILED_NOW = ConcurrentHashMap.newKeySet();
    /** Guarded by STATE: the running recorder (a ClassDefineRecorder, typed Object so this class links without jdk.jfr). */
    private static Object recorder;
    private static boolean recorderStarting;
    private static long windowSeq;
    private static CountDownLatch windowEnd;

    private ClassWarmup() {
    }

    // ------------------------------------------------------------------------------------------------ lifecycle (game)

    /** TitleScreen.init (render thread): the warm-up once per launch, and the recorder for the next join. */
    public static void onTitleScreen() {
        if (!enabled) return;
        if (WARM_STARTED.compareAndSet(false, true)) startWarmThread();
        startRecorder();
    }

    /** ClientPacketListener.handleLogin, after it (render thread): the join; the recorded window opens. */
    public static void onJoin() {
        if (!enabled) return;
        if (firstJoinNanos == 0L) firstJoinNanos = System.nanoTime();
        if (WARM_STARTED.compareAndSet(false, true)) startWarmThread();   // joined without a title screen (quick play)
        startRecorder();
        long from = epochNanosNow();
        CountDownLatch end = new CountDownLatch(1);
        long seq;
        synchronized (STATE) {
            if (windowEnd != null) windowEnd.countDown();                   // a window still open from an earlier join
            windowEnd = end;
            seq = ++windowSeq;
        }
        Thread t = new Thread(() -> finishWindow(seq, from, end), "Bons and Furious class recorder");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        t.start();
    }

    /**
     * Minecraft.clearLevel (render thread): the world is left; the window closes and the warm-up stops. clearLevel also runs before every join
     * (doWorldLoad, ConnectScreen.startConnecting): before the first join the warm-up goes on, as the switch's comment says (until 10 seconds after joining).
     */
    public static void onLeave() {
        if (!enabled) return;
        if (firstJoinNanos != 0L) stopWarm = true;
        synchronized (STATE) {
            if (windowEnd != null) windowEnd.countDown();
        }
    }

    private static void startWarmThread() {
        Thread t = new Thread(ClassWarmup::warmFromFile, THREAD_NAME);
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        t.setContextClassLoader(ClassWarmup.class.getClassLoader());
        t.start();
    }

    private static void startRecorder() {
        synchronized (STATE) {
            if (recorder != null || recorderStarting || WARNED_JFR.get()) return;
            recorderStarting = true;
        }
        Thread t = new Thread(() -> {
            Object r = null;
            try {
                r = ClassDefineRecorder.start(ClassWarmup.class.getClassLoader().getClass().getName(), THREAD_NAME);
            } catch (Throwable e) {
                if (WARNED_JFR.compareAndSet(false, true))
                    LOGGER.warn("Bons and Furious: client_class_warmup cannot record class loading in this Java runtime ({}); the class list is not updated",
                            e.toString());
            } finally {
                synchronized (STATE) {
                    recorder = r;
                    recorderStarting = false;
                }
            }
        }, "Bons and Furious class recorder start");
        t.setDaemon(true);
        t.start();
    }

    /** Window thread: waits 3 minutes (or the leave), merges the window's classes into the list and writes it. */
    private static void finishWindow(long seq, long fromEpochNanos, CountDownLatch end) {
        try {
            end.await(WINDOW_NANOS, TimeUnit.NANOSECONDS);
            long to = epochNanosNow();
            Object r;
            synchronized (STATE) {
                r = recorder;
            }
            if (r == null) return;
            List<ClassDefineRecorder.Defined> defined = ((ClassDefineRecorder) r).take(fromEpochNanos, to, 5000);
            WarmList base, merged;
            long warmed = 0, gameListed = 0, gameNew = 0;
            synchronized (LIST) {
                base = baseList();
                Set<String> listed = new HashSet<>(base.names);
                List<String> fresh = new ArrayList<>();
                for (ClassDefineRecorder.Defined d : defined) {
                    if (d.byWarmupThread()) warmed++;
                    else if (listed.contains(d.name())) gameListed++;
                    else gameNew++;
                    // since 1.0.49.2: a class the warm-up thread itself defined (a supertype or constructor parameter type of a listed class) is
                    // not one the game defined; the warm-up now runs into the first window, so it is left out of the list
                    if (!d.byWarmupThread() && warmable(d.name()) && listed.add(d.name())) fresh.add(d.name());
                }
                Set<String> failed = new LinkedHashSet<>(base.failed);
                failed.addAll(FAILED_NOW);
                merged = base.with(fresh, failed);
                list = merged;
                write(listFile(), merged);
            }
            VERIFY_WARMED_DEFINES.addAndGet(warmed);
            VERIFY_GAME_DEFINES_LISTED.addAndGet(gameListed);
            VERIFY_GAME_DEFINES_NEW.addAndGet(gameNew);
            if (VERIFY)
                LOGGER.info("Bons and Furious: client_class_warmup window {}: {} classes defined ({} by the warm-up thread, {} listed ones by the game itself, {} new)",
                        seq, defined.size(), warmed, gameListed, gameNew);
            String line = "Bons and Furious: client_class_warmup recorded " + (merged.names.size() - base.names.size())
                    + " more classes defined after joining (list now " + merged.names.size() + "); the next launch loads them in the background";
            if (INFO_DONE.compareAndSet(false, true)) LOGGER.info(line);
            else LOGGER.debug(line);
            synchronized (STATE) {
                if (seq == windowSeq && recorder == r) {          // no newer window: the stream stops until the next title screen
                    ((ClassDefineRecorder) r).close();
                    recorder = null;
                    windowEnd = null;
                }
            }
        } catch (Throwable t) {
            LOGGER.warn("Bons and Furious: client_class_warmup could not update its class list ({})", t.toString());
        }
    }

    /** The list read at start, or an empty one with this mod list's fingerprint. Caller holds LIST. */
    private static WarmList baseList() {
        if (list == null) {
            String fp = fingerprint();
            WarmList read = null;
            try {
                Path f = listFile();
                if (Files.isRegularFile(f)) read = parse(Files.readString(f, StandardCharsets.UTF_8));
            } catch (Throwable t) {
                LOGGER.debug("Bons and Furious: client_class_warmup could not read its class list ({})", t.toString());
            }
            list = read != null && fp.equals(read.fingerprint) ? read : new WarmList(fp, List.of(), Set.of());
        }
        return list;
    }

    /** Warm-up thread body. */
    private static void warmFromFile() {
        long start = System.nanoTime();
        WarmList base;
        synchronized (LIST) {
            base = baseList();
        }
        if (base.names.isEmpty()) return;
        Set<String> known = new HashSet<>(base.names.size() * 2);
        for (String n : base.names) known.add(n.replace('.', '/'));
        Result r = warm(base.names, base.failed, ClassWarmup.class.getClassLoader(), known, () -> stopWarm || !enabled
                || (firstJoinNanos != 0L && System.nanoTime() - firstJoinNanos > WARM_AFTER_JOIN_NANOS));
        FAILED_NOW.addAll(r.failedNames);
        String line = String.format("Bons and Furious: client_class_warmup loaded and linked %d of %d listed classes in the background "
                        + "(%d loaded only, %d failed, %d skipped, %d not reached) in %d ms", r.linked, base.names.size(), r.loadedOnly,
                r.failedNames.size(), r.skipped, r.notReached, (System.nanoTime() - start) / 1_000_000L);
        if (INFO_DONE.compareAndSet(false, true)) LOGGER.info(line);
        else LOGGER.debug(line);
    }

    // ------------------------------------------------------------------------------------------------ core (testable)

    /** What a warm-up did. */
    public static final class Result {
        public int linked, loadedOnly, skipped, notReached;
        public final List<String> failedNames = new ArrayList<>();
    }

    /**
     * Loads every warmable, not-failed name in order with Class.forName(name, false, loader) and links it with
     * getDeclaredConstructors() when {@link #linkSafe} allows; stops (the rest counted as not reached) when {@code stop}
     * answers true. Never initialises a class; a class that fails is listed in the result.
     */
    public static Result warm(List<String> names, Set<String> failedBefore, ClassLoader loader, Set<String> knownInternal, BooleanSupplier stop) {
        Result r = new Result();
        for (int i = 0; i < names.size(); i++) {
            if (stop.getAsBoolean()) {
                r.notReached = names.size() - i;
                break;
            }
            String n = names.get(i);
            if (failedBefore.contains(n) || !warmable(n)) {
                r.skipped++;
                continue;
            }
            try {
                Class<?> c = Class.forName(n, false, loader);
                if (linkSafe(classBytes(loader, n), n.replace('.', '/'), knownInternal)) {
                    c.getDeclaredConstructors();
                    r.linked++;
                } else {
                    r.loadedOnly++;
                }
            } catch (VirtualMachineError e) {
                throw e;
            } catch (Throwable t) {
                r.failedNames.add(n);
                LOGGER.debug("Bons and Furious: client_class_warmup skipped {} ({})", n, t.toString());
            }
        }
        return r;
    }

    /** A name the warm-up may ask for: a plain binary class name outside the JDK, Mixin, MixinExtras and mixin packages. */
    public static boolean warmable(String n) {
        if (n == null || n.isEmpty() || n.length() > 400) return false;
        for (int i = 0; i < n.length(); i++) {
            char c = n.charAt(i);
            if (!(Character.isLetterOrDigit(c) || c == '.' || c == '$' || c == '_')) return false;
        }
        if (n.contains("$$Lambda") || n.startsWith(".") || n.endsWith(".") || n.contains("..")) return false;
        if (n.startsWith("java.") || n.startsWith("javax.") || n.startsWith("jdk.") || n.startsWith("sun.") || n.startsWith("com.sun.")
                || n.startsWith("org.spongepowered.") || n.startsWith("com.llamalad7.mixinextras.") || n.startsWith("bons.furious.mixin."))
            return false;
        return !(n.contains(".mixin.") || n.contains(".mixins."));
    }

    /**
     * True when linking cannot ask for a class outside the JDK, Minecraft/Mojang/Forge and the list through a constructor
     * signature: every constructor parameter type (array element types too) is one of those, or the class itself.
     * Unreadable bytes -> false.
     */
    public static boolean linkSafe(byte[] bytes, String selfInternal, Set<String> knownInternal) {
        if (bytes == null) return false;
        ClassNode node = new ClassNode();
        try {
            new ClassReader(bytes).accept(node, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        } catch (RuntimeException e) {
            return false;
        }
        for (MethodNode m : node.methods) {
            if (!m.name.equals("<init>")) continue;
            for (Type t : Type.getArgumentTypes(m.desc)) {
                Type e = t.getSort() == Type.ARRAY ? t.getElementType() : t;
                if (e.getSort() != Type.OBJECT) continue;
                String in = e.getInternalName();
                if (in.equals(selfInternal) || knownInternal.contains(in)) continue;
                if (in.startsWith("java/") || in.startsWith("javax/") || in.startsWith("jdk/") || in.startsWith("net/minecraft/")
                        || in.startsWith("com/mojang/") || in.startsWith("net/minecraftforge/")) continue;
                return false;
            }
        }
        return true;
    }

    static byte[] classBytes(ClassLoader loader, String binaryName) {
        try (InputStream in = loader.getResourceAsStream(binaryName.replace('.', '/') + ".class")) {
            return in == null ? null : in.readAllBytes();
        } catch (Throwable t) {
            return null;
        }
    }

    /** The list file's content. */
    public static final class WarmList {
        public final String fingerprint;
        public final List<String> names;
        public final Set<String> failed;

        public WarmList(String fingerprint, List<String> names, Set<String> failed) {
            this.fingerprint = fingerprint;
            this.names = List.copyOf(names);
            this.failed = Set.copyOf(failed);
        }

        /** This list + the fresh names at the end (up to MAX_ENTRIES in all), with this failed set (listed names only). */
        public WarmList with(List<String> fresh, Set<String> failedNow) {
            List<String> all = new ArrayList<>(names);
            for (String n : fresh) {
                if (all.size() >= MAX_ENTRIES) break;
                all.add(n);
            }
            Set<String> f = new LinkedHashSet<>(failedNow);
            f.retainAll(new HashSet<>(all));
            return new WarmList(fingerprint, all, f);
        }
    }

    /** Parses a list file; null when it has no fingerprint line. Unknown, malformed and repeated lines are ignored. */
    public static WarmList parse(String text) {
        String fp = null;
        List<String> names = new ArrayList<>();
        Set<String> failed = new LinkedHashSet<>();
        Set<String> seen = new HashSet<>();
        for (String raw : text.split("\n")) {
            String line = raw.strip();
            if (line.isEmpty() || line.startsWith("#")) continue;
            if (line.startsWith("fingerprint=")) {
                if (fp == null) fp = line.substring("fingerprint=".length()).strip();
                continue;
            }
            boolean bad = line.startsWith("!");
            String n = bad ? line.substring(1) : line;
            if (!warmable(n) || !seen.add(n)) continue;
            names.add(n);
            if (bad) failed.add(n);
        }
        return fp == null || fp.isEmpty() ? null : new WarmList(fp, names, failed);
    }

    /** The list file's text (UTF-8, no BOM, '\n' line ends). */
    public static String format(WarmList l) {
        StringBuilder sb = new StringBuilder(HEADER.length() + l.names.size() * 48);
        sb.append(HEADER).append("fingerprint=").append(l.fingerprint).append('\n');
        for (String n : l.names) {
            if (l.failed.contains(n)) sb.append('!');
            sb.append(n).append('\n');
        }
        return sb.toString();
    }

    /** Writes the list through a temporary file in the same folder (moved into place). */
    public static void write(Path file, WarmList l) throws java.io.IOException {
        Files.createDirectories(file.getParent());
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.write(tmp, format(l).getBytes(StandardCharsets.UTF_8));
        try {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.io.IOException e) {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** sha256 over the sorted lines (each: mod id, version, file name). */
    public static String fingerprint(Collection<String> lines) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            for (String s : new TreeSet<>(lines)) {
                md.update(s.getBytes(StandardCharsets.UTF_8));
                md.update((byte) '\n');
            }
            StringBuilder sb = new StringBuilder();
            for (byte b : md.digest()) sb.append(String.format("%02x", b & 0xFF));
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** This launch's mod-list fingerprint (Forge's mod list: id, version and file of every mod). Caller holds LIST. */
    private static String fingerprint() {
        if (fingerprint == null) {
            List<String> lines = new ArrayList<>();
            for (net.minecraftforge.forgespi.language.IModInfo m : net.minecraftforge.fml.ModList.get().getMods()) {
                String file;
                try {
                    file = m.getOwningFile().getFile().getFileName();
                } catch (Throwable t) {
                    file = "?";
                }
                lines.add(m.getModId() + " " + m.getVersion() + " " + file);
            }
            fingerprint = fingerprint(lines);
        }
        return fingerprint;
    }

    private static Path listFile() {
        return net.minecraftforge.fml.loading.FMLPaths.CONFIGDIR.get().resolve("bons_and_furious").resolve("warm-classes.txt");
    }

    /** Wall-clock nanoseconds, the time base of JFR's event start times (without touching jdk.jfr). */
    public static long epochNanosNow() {
        java.time.Instant i = java.time.Instant.now();
        return i.getEpochSecond() * 1_000_000_000L + i.getNano();
    }
}
