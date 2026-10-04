package bons.furious.patch.dh_loader;

import java.io.ByteArrayInputStream;
import java.util.Objects;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch distanthorizons_unlocked_byte_stream (Distant Horizons 3.3.3 for Minecraft 1.21.1 / NeoForge,
 * tested build DistantHorizons-3.3.3-1.21.1-fabric-neoforge.jar, LGPL-3.0; both sides). No Distant Horizons code here.
 *
 * DhDataInputStream.create wraps every LOD blob DH reads back from its database (the column data, the id map, generation
 * steps) in a java.io.ByteArrayInputStream, and DH then reads it through DataInputStream mostly one byte at a time: a
 * varint per value, readByte per character of every block and biome name. ByteArrayInputStream.read() is synchronized, so
 * on Java 21 every single byte costs a monitor enter and exit (two atomic operations; nothing else ever locks these
 * streams).
 *
 * The stream created there is now this subclass. Its read() and read(byte[], int, int) do exactly what
 * ByteArrayInputStream's do (same fields, same bounds, same results and exceptions) without taking the monitor; every other
 * method is ByteArrayInputStream's own. Leaving out the lock changes nothing observable: the stream is created inside
 * create() and used only by the thread that called it (DH opens these streams in try-with-resources blocks), and no code
 * synchronizes on it. With the runtime switch off, create() gets a plain ByteArrayInputStream as before.
 *
 * Ported to 1.21.1: no change. DhDataInputStream is byte-identical in DH 3.3.3 (create(byte[], mode, checkout) still
 * holds the two NEW ByteArrayInputStream, (byte[], int, int) first and (byte[]) second), its seven caller methods are the
 * same (six byte-identical; FullDataSourceV2Repo.getColumnGenerationStepForPos differs only in its SQL-exception check),
 * and the 1.21.1 game runs on Java 21 as the 1.20.1 pack does (ByteArrayInputStream's fields and read semantics as above).
 */
public final class UnlockedByteStream extends ByteArrayInputStream {
    /** Runtime switch (the config switch acts when classes are transformed). -Dbons_and_furious.dhUnlockedByteStream=false also turns it off. */
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.dhUnlockedByteStream", "true"));
    /** Streams created as this class (one per blob read; probes). */
    public static final java.util.concurrent.atomic.AtomicLong CREATED = new java.util.concurrent.atomic.AtomicLong();
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    private static volatile boolean announced;

    private UnlockedByteStream(byte[] buf) {
        super(buf);
    }

    private UnlockedByteStream(byte[] buf, int offset, int length) {
        super(buf, offset, length);
    }

    /** In place of new ByteArrayInputStream(buf) in DhDataInputStream.create. */
    public static ByteArrayInputStream of(byte[] buf) {
        if (!enabled) return new ByteArrayInputStream(buf);
        announce();
        return new UnlockedByteStream(buf);
    }

    /** In place of new ByteArrayInputStream(buf, offset, length) in DhDataInputStream.create. */
    public static ByteArrayInputStream of(byte[] buf, int offset, int length) {
        if (!enabled) return new ByteArrayInputStream(buf, offset, length);
        announce();
        return new UnlockedByteStream(buf, offset, length);
    }

    private static void announce() {
        CREATED.incrementAndGet();
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: distanthorizons_unlocked_byte_stream reads Distant Horizons' LOD blobs without a lock per byte");
        }
    }

    @Override
    public int read() {
        return pos < count ? buf[pos++] & 0xFF : -1;
    }

    @Override
    public int read(byte[] b, int off, int len) {
        Objects.checkFromIndexSize(off, len, b.length);
        if (pos >= count) return -1;
        int available = count - pos;
        if (len > available) len = available;
        if (len <= 0) return 0;
        System.arraycopy(buf, pos, b, off, len);
        pos += len;
        return len;
    }
}
