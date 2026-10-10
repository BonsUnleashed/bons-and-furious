package bons.furious.patch.distanthorizons_streams;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch distanthorizons_unlocked_output_stream (Distant Horizons 3.3.3 for Minecraft 1.21.1 / NeoForge,
 * tested build DistantHorizons-3.3.3-1.21.1-fabric-neoforge.jar, LGPL-3.0; both sides: DH saves LOD data on the client
 * and on a server that runs DH). No Distant Horizons code here.
 *
 * Every LOD blob DH writes to its database (FullDataSourceV2DTO: the column data, the adjacent edges, the generation steps,
 * the compression modes, the id map) goes through DhDataOutputStream.create, which wraps a new java.io.ByteArrayOutputStream.
 * DH writes most of it one byte at a time: VarintUtil.writeVarint calls writeByte per varint byte for every column size and
 * every data point's id, height and offset. ByteArrayOutputStream.write(int) and write(byte[], int, int) are synchronized,
 * so on Java 21 every single byte costs a monitor enter and exit (two atomic operations); nothing ever contends for it.
 *
 * The stream DhDataOutputStream.create builds is now this subclass (UnlockedOutputStreamMixin redirects the construction).
 * Its write(int), write(byte[], int, int), toByteArray(), size(), reset() and writeTo(OutputStream) do what
 * ByteArrayOutputStream's do - the same fields, the same growth policy (ArraysSupport.newLength's arithmetic: grow to
 * max(needed, double), soft limit Integer.MAX_VALUE - 8, the same OutOfMemoryError text), the same bounds check and
 * exceptions - only without the monitor. Every other method is ByteArrayOutputStream's own. Leaving out the lock changes
 * nothing observable: DH creates the stream inside DhDataOutputStream.create, keeps it in a private final field, and all
 * five callers (FullDataSourceV2DTO.write*ToBlob*) write and close it on their own thread inside one method; no code
 * synchronizes on it. The bytes DH stores are the same bytes. With the runtime switch off, create() returns a plain
 * ByteArrayOutputStream as before.
 *
 * -Dbons_and_furious.dhUnlockedOutputStream=false builds plain streams; -Dbons_and_furious.dhUnlockedOutputStream.shadow=true
 * (verification runs only) also writes every byte into a plain ByteArrayOutputStream and compares the two buffers whenever
 * DH takes the bytes (toByteArray / writeTo) or asks the size (SHADOW_CHECKS / SHADOW_MISMATCHES).
 *
 * Ported to 1.21.1: no change. Every DH class involved is byte-identical in DH 3.3.3 (DhDataOutputStream and its inner
 * class, VarintUtil, FullDataSourceV2DTO, FullDataPointIdMap, FullDataSourceV1: the same callers, the same single-thread
 * use), and the 1.21.1 game runs on Java 21 as the 1.20.1 pack does (ByteArrayOutputStream's fields and growth as above).
 */
public class UnlockedOutputStream extends ByteArrayOutputStream {
    /** Runtime switch (the config switch acts when classes are transformed). */
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.dhUnlockedOutputStream", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.dhUnlockedOutputStream.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    /** Streams created as this class (one per blob written; probes). */
    public static final AtomicLong CREATED = new AtomicLong();
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    /** java.util.ArraysSupport.SOFT_MAX_ARRAY_LENGTH (JDK 17-25). */
    private static final int SOFT_MAX_ARRAY_LENGTH = Integer.MAX_VALUE - 8;
    private static volatile boolean announced;

    UnlockedOutputStream() {
        super();
    }

    /** In place of new ByteArrayOutputStream() in DhDataOutputStream.create. */
    public static ByteArrayOutputStream create() {
        if (!enabled) return new ByteArrayOutputStream();
        CREATED.incrementAndGet();
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: distanthorizons_unlocked_output_stream writes Distant Horizons' LOD blobs without a lock per byte{}",
                    SHADOW ? " - shadow verification on" : "");
        }
        return SHADOW ? new Shadow() : new UnlockedOutputStream();
    }

    /** ByteArrayOutputStream.ensureCapacity with ArraysSupport.newLength(oldLength, minGrowth, oldLength) spelled out. */
    private void ensureCapacity(int minCapacity) {
        int oldCapacity = buf.length;
        int minGrowth = minCapacity - oldCapacity;
        if (minGrowth > 0) {
            int prefLength = oldCapacity + Math.max(minGrowth, oldCapacity);   // may overflow
            int newLength;
            if (0 < prefLength && prefLength <= SOFT_MAX_ARRAY_LENGTH) {
                newLength = prefLength;
            } else {
                int minLength = oldCapacity + minGrowth;
                if (minLength < 0) throw new OutOfMemoryError("Required array length " + oldCapacity + " + " + minGrowth + " is too large");
                newLength = minLength <= SOFT_MAX_ARRAY_LENGTH ? SOFT_MAX_ARRAY_LENGTH : minLength;
            }
            buf = Arrays.copyOf(buf, newLength);
        }
    }

    @Override
    public void write(int b) {
        ensureCapacity(count + 1);
        buf[count] = (byte) b;
        count += 1;
    }

    @Override
    public void write(byte[] b, int off, int len) {
        if (b == null) {
            super.write(null, off, len);   // the JDK's own NullPointerException (its helpful message names the parameter "b")
            return;
        }
        Objects.checkFromIndexSize(off, len, b.length);
        ensureCapacity(count + len);
        System.arraycopy(b, off, buf, count, len);
        count += len;
    }

    @Override
    public void writeTo(OutputStream out) throws IOException {
        if (out == null) {
            super.writeTo(null);           // the JDK's own NullPointerException, as above
            return;
        }
        out.write(buf, 0, count);
    }

    @Override
    public void reset() {
        count = 0;
    }

    @Override
    public byte[] toByteArray() {
        return Arrays.copyOf(buf, count);
    }

    @Override
    public int size() {
        return count;
    }

    /** Shadow mode: the same stream plus a plain ByteArrayOutputStream fed with every call, compared when DH reads it. */
    static final class Shadow extends UnlockedOutputStream {
        private final ByteArrayOutputStream plain = new ByteArrayOutputStream();

        @Override
        public void write(int b) {
            super.write(b);
            plain.write(b);
        }

        @Override
        public void write(byte[] b, int off, int len) {
            super.write(b, off, len);
            plain.write(b, off, len);
        }

        @Override
        public void reset() {
            super.reset();
            plain.reset();
        }

        @Override
        public byte[] toByteArray() {
            byte[] ours = super.toByteArray();
            check(ours, plain.toByteArray(), "toByteArray");
            return ours;
        }

        @Override
        public void writeTo(OutputStream out) throws IOException {
            check(super.toByteArray(), plain.toByteArray(), "writeTo");
            super.writeTo(out);
        }

        @Override
        public int size() {
            int ours = super.size();
            SHADOW_CHECKS.incrementAndGet();
            if (ours != plain.size() && SHADOW_MISMATCHES.incrementAndGet() <= 20)
                LOGGER.warn("Bons and Furious: distanthorizons_unlocked_output_stream shadow mismatch: size {} vs {}", ours, plain.size());
            return ours;
        }

        private static void check(byte[] ours, byte[] plainBytes, String what) {
            SHADOW_CHECKS.incrementAndGet();
            if (!Arrays.equals(ours, plainBytes) && SHADOW_MISMATCHES.incrementAndGet() <= 20)
                LOGGER.warn("Bons and Furious: distanthorizons_unlocked_output_stream shadow mismatch in {}: {} bytes vs {}", what, ours.length, plainBytes.length);
        }
    }
}
