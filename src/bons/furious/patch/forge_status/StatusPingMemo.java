package bons.furious.patch.forge_status;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.ServerStatusPing;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch forge_status_ping_memo (Forge 47.4.16, both sides). No Forge code here.
 *
 * MinecraftServer.resetStatusCache encodes the whole server-list status, including Forge's "d" field: ServerStatusPing.toBuf()
 * writes every mod (id, version, its channels found by filtering ALL channels for each mod: mods x channels comparisons)
 * into a buffer, and the private encodeOptimized packs those bytes into a string. The mod list and channels never change
 * in a running game, but the status is rebuilt every 5 s, and every TICK while Chunk Pregenerator shows its progress in
 * the server-list text (16.6% of the dedicated server thread during a pregeneration, our own recording).
 *
 * toBuf: the ping's mods and channels are read in iteration order (mod id, version; channel id, its data's id, version,
 * required); when that sequence equals the one of the last successful toBuf, a new buffer with the same bytes is returned
 * (same type, reader index 0, writer index = length; since 1.0.34 also the same capacity and maximum capacity). toBuf's
 * output is a function of exactly that sequence: it writes the mods in order, for each the channels of its namespace in
 * channel order, then the channels whose namespace is no mod, and stops at 60,000 bytes. encodeOptimized: when the
 * readable bytes equal the last input, the buffer is consumed and released exactly as Forge's method does and the same
 * text is returned. Anything else runs Forge's code. Nothing is remembered when Forge's code throws.
 */
public final class StatusPingMemo {
    /** Runtime switch (the config switch acts when classes are transformed). -Dbons_and_furious.forgeStatusPingMemo=false also turns it off. */
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.forgeStatusPingMemo", "true"));
    /** Shadow mode for rigs: every remembered result is also computed by Forge's code and compared (WARN on a difference). */
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.forgeStatusPingMemo.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    /** Counters (read by probes): toBuf answered from the memo, encodeOptimized answered from the memo. */
    public static final AtomicLong BUF_HITS = new AtomicLong(), TEXT_HITS = new AtomicLong();
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    private static volatile boolean announced;

    private record Built(Object[] snapshot, byte[] bytes, int capacity, int maxCapacity) {}

    private record Packed(byte[] in, String out) {}

    private static volatile Built built;
    private static volatile Packed packed;

    private StatusPingMemo() {
    }

    /** The ping's content as toBuf reads it, in iteration order. */
    public static Object[] snapshot(Map<ResourceLocation, ServerStatusPing.ChannelData> channels, Map<String, String> mods) {
        Object[] s = new Object[2 + 2 * mods.size() + 4 * channels.size()];
        int i = 0;
        s[i++] = mods.size();
        s[i++] = channels.size();
        for (Map.Entry<String, String> e : mods.entrySet()) {
            s[i++] = e.getKey();
            s[i++] = e.getValue();
        }
        for (Map.Entry<ResourceLocation, ServerStatusPing.ChannelData> e : channels.entrySet()) {
            ServerStatusPing.ChannelData d = e.getValue();
            s[i++] = e.getKey();
            s[i++] = d == null ? null : d.res();
            s[i++] = d == null ? null : d.version();
            s[i++] = d == null ? null : d.required();
        }
        return s;
    }

    /** A new buffer with the bytes of the last toBuf when its input sequence was the same, else null. */
    public static ByteBuf cachedBuf(Object[] snapshot) {
        Built b = built;
        if (b == null || !Arrays.equals(b.snapshot, snapshot)) return null;
        BUF_HITS.incrementAndGet();
        // 1.0.34: the buffer toBuf itself makes (Unpooled.buffer: unpooled heap) with its capacity and maximum capacity
        // (Integer.MAX_VALUE); Unpooled.copiedBuffer gave a buffer whose maximum capacity was its length
        return new FriendlyByteBuf(Unpooled.buffer(b.capacity, b.maxCapacity).writeBytes(b.bytes));
    }

    public static void rememberBuf(Object[] snapshot, ByteBuf result) {
        built = new Built(snapshot, readable(result), result.capacity(), result.maxCapacity());
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: forge_status_ping_memo reuses Forge's server-list mod data while the mod list is unchanged");
        }
    }

    /** The readable bytes, without moving the reader index. */
    public static byte[] readable(ByteBuf buf) {
        byte[] b = new byte[buf.readableBytes()];
        buf.getBytes(buf.readerIndex(), b);
        return b;
    }

    /**
     * encodeOptimized's text for these bytes when they equal the last input, consuming and releasing the buffer as Forge's
     * method does (reader index to the writer index, one release); null otherwise (the buffer is left untouched).
     */
    public static String cachedText(ByteBuf buf) {
        Packed p = packed;
        int n = buf.readableBytes();
        if (p == null || p.in.length != n || !ByteBufUtil.equals(buf, buf.readerIndex(), Unpooled.wrappedBuffer(p.in), 0, n)) return null;
        TEXT_HITS.incrementAndGet();
        buf.readerIndex(buf.writerIndex());
        buf.release();
        return p.out;
    }

    public static void rememberText(byte[] in, String out) {
        packed = new Packed(in, out);
    }

    public static void shadow(String what, boolean same) {
        SHADOW_CHECKS.incrementAndGet();
        if (!same && SHADOW_MISMATCHES.incrementAndGet() <= 20)
            LOGGER.warn("Bons and Furious: forge_status_ping_memo shadow check: {} differs from Forge's own result", what);
    }
}
