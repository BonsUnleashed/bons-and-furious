package bons.furious.patch.save_writer;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.zip.GZIPOutputStream;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;

/**
 * Bons and Furious switches vanilla_background_saves / vanilla_background_level_dat (Minecraft 1.20.1 on Forge 47.4.16;
 * acts on the server thread of a running server): the uncompressed bytes of one NbtIo.writeCompressed call, taken on the
 * server thread, and their replay into the file on the writer thread.
 *
 * Minecraft's NbtIo.writeCompressed(tag, File) (m_128944_) opens a FileOutputStream and calls writeCompressed(tag,
 * OutputStream) (m_128947_), which writes NbtIo.write(tag, out) (m_128941_) through
 * DataOutputStream -> BufferedOutputStream -> GZIPOutputStream -> the file. Recording keeps the first two layers and puts
 * this stream where the GZIPOutputStream would be: it receives exactly the calls the GZIPOutputStream would receive
 * (8192-byte buffer flushes, large pass-through writes, the final partial buffer, flush, flush, close), in the same order,
 * because the BufferedOutputStream is built on the same (platform) thread with the same size.
 *
 * Replay builds the same three layers over a FileOutputStream on the writer thread and feeds the recorded writes through
 * the DataOutputStream: an 8192-byte or larger write passes straight through an empty BufferedOutputStream, a smaller one
 * is buffered exactly as the original buffer held it, so the GZIPOutputStream gets the same calls with the same lengths
 * and its Deflater produces the same bytes. Flushes recorded while NbtIo.write ran are replayed as flushes; the flushes and
 * close that came from closing the streams are produced by the replay's own try-with-resources, as in Minecraft.
 *
 * When NbtIo.write threw (a mod's tag that cannot be written), the recording keeps the bytes written before the throw and
 * the throwable; writing it reproduces Minecraft's result: the file holds a complete gzip of those bytes and the throwable
 * propagates with any close failure added as suppressed.
 */
public final class NbtRecording extends OutputStream {
    private static final int FLUSH = -1, CLOSE = -2, BYTE = -3;
    private byte[] data = new byte[8192];
    private int size;
    private int[] ops = new int[16];
    private int nops;
    /** Number of ops recorded while NbtIo.write ran; the rest came from closing the streams. */
    private int writeOps = -1;
    /** What NbtIo.write threw, or null. */
    Throwable failure;

    private NbtRecording() {
    }

    /** Server thread: the bytes NbtIo.writeCompressed would deflate for this tag. */
    public static NbtRecording record(CompoundTag tag) {
        NbtRecording rec = new NbtRecording();
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(rec))) {
            try {
                NbtIo.m_128941_(tag, out);
            } finally {
                rec.writeOps = rec.nops;
            }
        } catch (Throwable t) {
            rec.failure = t;
        }
        return rec;
    }

    public Throwable failure() {
        return this.failure;
    }

    /** Uncompressed byte count (for the counters). */
    public int size() {
        return this.size;
    }

    private void op(int code) {
        if (this.nops == this.ops.length) this.ops = Arrays.copyOf(this.ops, this.ops.length * 2);
        this.ops[this.nops++] = code;
    }

    private void room(int n) {
        if (this.size + n > this.data.length) this.data = Arrays.copyOf(this.data, Math.max(this.data.length * 2, this.size + n));
    }

    @Override
    public void write(int b) {
        room(1);
        this.data[this.size++] = (byte) b;
        op(BYTE);
    }

    @Override
    public void write(byte[] b, int off, int len) {
        if ((off | len) < 0 || len > b.length - off) throw new IndexOutOfBoundsException();
        room(len);
        System.arraycopy(b, off, this.data, this.size, len);
        this.size += len;
        op(len);
    }

    @Override
    public void flush() {
        op(FLUSH);
    }

    @Override
    public void close() {
        op(CLOSE);
    }

    /** Writer thread (or the server thread for a failed recording): NbtIo.writeCompressed(tag, file) with the recorded bytes. */
    public void writeTo(File file) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(file)) {
            writeTo(fos);
        }
    }

    /** NbtIo.writeCompressed(tag, OutputStream) with the recorded bytes (also used by the shadow check into memory). */
    public void writeTo(OutputStream target) throws IOException {
        try (DataOutputStream dos = new DataOutputStream(new BufferedOutputStream(new GZIPOutputStream(target)))) {
            int pos = 0;
            for (int i = 0; i < this.nops; i++) {
                int code = this.ops[i];
                boolean writePhase = i < this.writeOps;
                if (code >= 0) {
                    dos.write(this.data, pos, code);
                    pos += code;
                } else if (code == BYTE) {
                    dos.write(this.data[pos++]);
                } else if (code == FLUSH && writePhase) {
                    dos.flush();
                }
                // FLUSH/CLOSE ops of the close phase are produced again by this try-with-resources
            }
            if (this.failure != null) throw NbtRecording.<RuntimeException>sneaky(this.failure);
        }
    }

    @SuppressWarnings("unchecked")
    static <T extends Throwable> T sneaky(Throwable t) throws T {
        throw (T) t;
    }
}
