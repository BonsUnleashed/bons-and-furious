package bons.furious.patch.client_class_warmup;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import jdk.jfr.consumer.RecordedClass;
import jdk.jfr.consumer.RecordedClassLoader;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedThread;
import jdk.jfr.consumer.RecordingStream;

/**
 * client_class_warmup's recorder: a JDK Flight Recorder stream with one event enabled, jdk.ClassDefine (no stack traces),
 * that keeps the binary name, the time and the defining thread of every class defined by ONE class loader type (the
 * game's TransformingClassLoader in game; the harness passes its own). Hidden classes and every other loader's classes are
 * not kept. All jdk.jfr use of the switch is in this class, so a Java runtime without the jdk.jfr module only loses the
 * recording (ClassWarmup catches the NoClassDefFoundError). The stream keeps at most 60 s / 32 MB of its own repository
 * data on disk and stops with close(); the kept list is capped at {@link #CAP} entries.
 */
public final class ClassDefineRecorder {
    /** At most this many defines are kept between two take() calls (the rest are dropped and counted). */
    public static final int CAP = 250_000;

    /** One recorded define. */
    public record Defined(String name, long epochNanos, boolean byWarmupThread) {}

    private final String loaderType;
    private final String warmupThreadName;
    private final RecordingStream stream;
    private final ArrayList<Defined> defines = new ArrayList<>();
    private final Object flushLock = new Object();
    private long flushes;
    private volatile long dropped;

    private ClassDefineRecorder(String loaderType, String warmupThreadName) {
        this.loaderType = loaderType;
        this.warmupThreadName = warmupThreadName;
        RecordingStream rs = new RecordingStream();
        try {
            rs.enable("jdk.ClassDefine").withoutStackTrace();
            rs.setMaxAge(Duration.ofSeconds(60));
            rs.setMaxSize(32L << 20);
            rs.setReuse(true);
            rs.setOrdered(true);
            rs.onEvent("jdk.ClassDefine", this::onEvent);
            rs.onFlush(this::onFlush);
        } catch (RuntimeException | Error e) {
            rs.close();
            throw e;
        }
        this.stream = rs;
    }

    /** Starts a recorder (asynchronous stream) for classes defined by loaders whose class name is {@code loaderType}. */
    public static ClassDefineRecorder start(String loaderType, String warmupThreadName) {
        ClassDefineRecorder r = new ClassDefineRecorder(loaderType, warmupThreadName);
        r.stream.startAsync();
        return r;
    }

    private void onEvent(RecordedEvent e) {
        RecordedClassLoader loader = e.getValue("definingClassLoader");
        if (loader == null) return;
        RecordedClass type = loader.getType();
        if (type == null || !loaderType.equals(type.getName())) return;
        RecordedClass defined = e.getClass("definedClass");
        if (defined == null) return;
        String name = defined.getName();
        if (name == null || name.indexOf('/') >= 0) return;          // hidden classes (Lambda/0x...) cannot be loaded by name
        RecordedThread thread = e.getThread();
        boolean byWarmup = thread != null && warmupThreadName.equals(thread.getJavaName());
        Instant at = e.getStartTime();
        long epochNanos = at.getEpochSecond() * 1_000_000_000L + at.getNano();
        synchronized (defines) {
            if (defines.size() < CAP) defines.add(new Defined(name, epochNanos, byWarmup));
            else dropped++;
        }
    }

    private void onFlush() {
        synchronized (flushLock) {
            flushes++;
            flushLock.notifyAll();
        }
    }

    /**
     * The defines with a time in [fromEpochNanos, toEpochNanos], in time order, after waiting (at most
     * {@code waitMillis}) for two more stream flushes so events up to now have arrived; everything up to
     * {@code toEpochNanos} is then forgotten.
     */
    public List<Defined> take(long fromEpochNanos, long toEpochNanos, long waitMillis) throws InterruptedException {
        long until = System.nanoTime() + waitMillis * 1_000_000L;
        synchronized (flushLock) {
            long target = flushes + 2;
            while (flushes < target) {
                long left = until - System.nanoTime();
                if (left <= 0) break;
                flushLock.wait(Math.max(1L, left / 1_000_000L));
            }
        }
        List<Defined> out = new ArrayList<>();
        synchronized (defines) {
            List<Defined> keep = new ArrayList<>();
            for (Defined d : defines) {
                if (d.epochNanos() > toEpochNanos) keep.add(d);
                else if (d.epochNanos() >= fromEpochNanos) out.add(d);
            }
            defines.clear();
            defines.addAll(keep);
        }
        out.sort(java.util.Comparator.comparingLong(Defined::epochNanos));
        return out;
    }

    /** Defines dropped because more than CAP were waiting. */
    public long dropped() {
        return dropped;
    }

    /** Stops the stream (its repository data is removed by the JDK). */
    public void close() {
        stream.close();
    }

    /** Wall-clock nanoseconds (the time base of JFR's event start times). */
    public static long epochNanosNow() {
        Instant now = Instant.now();
        return now.getEpochSecond() * 1_000_000_000L + now.getNano();
    }
}
