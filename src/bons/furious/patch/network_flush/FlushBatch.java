package bons.furious.patch.network_flush;

import com.mojang.logging.LogUtils;
import io.netty.channel.Channel;
import io.netty.util.concurrent.AbstractEventExecutor;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundKeepAlivePacket;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.transformer.meta.MixinMerged;

/**
 * Bons and Furious switch vanilla_connection_flush_batching (Minecraft 1.20.1 on Forge 47.4.16 with Netty 4.1.82, both
 * sides; acts on the server thread). SRG member names.
 *
 * Connection.send hands every packet sent from the server thread to the connection's Netty event loop as its own task
 * (EventLoop.execute: queue it, and wake the event loop with a selector wakeup when it sleeps - a system call on the server
 * thread), and that task writes and flushes it (Channel.writeAndFlush: one socket write per packet). The entity tracker
 * and the chunk-change broadcast send hundreds of packets per tick in one burst, and a player's own tick (chunk sending,
 * inventory and status updates) another burst to that player.
 *
 * Inside such a burst (a "batch" opened on the server thread around ServerChunkCache.tickChunks, which ends with the
 * entity tracker, and around Connection.tick, for that connection only) a packet without a send listener is handed over
 * with lazyExecute instead: the same task, appended to the same task queue in the same order, but without waking the
 * event loop; when the task runs, it writes the packet without flushing (BatchedWrite + the Connection mixin). At the end
 * of the burst every connection that got such packets is flushed once (Channel.flush, which also wakes the event loop):
 * the tasks run in their original order, the encoders produce the same bytes in the same order (compression and the
 * stream cipher see the same sequence), and the bytes leave in fewer socket writes before the tick ends. A connection's own
 * tick (Connection.tick) is ended the same way, whatever happens to Minecraft's own flush at the end of tick (1.0.36: it is
 * no longer counted as the burst's flush, since another mod may remove it - VMP's networking.no_flush in HariPlayer 2.0 -
 * which left the batched login packets unwritten; with that flush intact the cost is one empty flush task per connection
 * tick that sent batched packets).
 *
 * 1.0.36: the burst's flush is queued on the connection's current event loop, behind the writes handed to that loop. A mod
 * that moves connections to another event loop (VMP's networking.eventloops, also in HariPlayer 2.0, re-registers a
 * channel when its protocol changes) could let that flush run before writes still queued on the old loop, which would
 * then wait for a later flush. When another mod's mixin on Connection does that (a known mover, or a Connection mixin
 * that calls Channel.deregister), the switch stands down once (one INFO line) and every packet takes Minecraft's path.
 *
 * Unchanged (Minecraft's path): packets with a send listener (disconnects, ...), keep-alive packets, packets that switch
 * the protocol (doSendPacket writes and flushes them), packets sent from any other thread or outside a burst, packets to
 * other connections during a connection's own tick, packets queued before a connection was active (flushQueue), and
 * event loops that are not Netty AbstractEventExecutors. Other threads' packets still go through execute and
 * writeAndFlush, which also flushes the batched packets queued before them, so per connection the bytes are identical.
 * To keep Netty's unflushed bytes small, a batched write first flushes when the connection is within 32 KB of Netty's
 * high-water mark (64 KB) - a flush earlier than the burst end, never a different byte.
 * 1.0.34: a connection disconnected during a burst (Connection.disconnect, e.g. a login kick sent without a listener) is
 * flushed before its channel closes (beforeDisconnect), since a close drops writes that were never flushed.
 *
 * -Dbons_and_furious.connectionFlushBatching=false: Minecraft's path for every packet.
 */
public final class FlushBatch {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.connectionFlushBatching", "true"));
    /** Counters for rigs: packets handed over without a wakeup, end-of-burst flushes, early flushes near the water mark. */
    public static final AtomicLong BATCHED = new AtomicLong(), FLUSHES = new AtomicLong(), GUARD_FLUSHES = new AtomicLong();
    /** 1.0.34, counter for rigs: flushes made because a connection with batched packets was disconnected (beforeDisconnect). */
    public static final AtomicLong DISCONNECT_FLUSHES = new AtomicLong();
    /** Before a batched write: flush first when fewer than this many bytes are left before Netty marks the channel unwritable. */
    public static final int GUARD_BYTES = 32 * 1024;

    /** The connection-side state the batch needs (implemented by the Connection mixin). */
    public interface Member {
        /** Event loop thread only: the next doSendPacket write of this connection is a batched one (write without flush). */
        void bons$writeOnly(boolean value);

        /** Server thread (batch owner) only: the batch generation this connection was last registered in. */
        int bons$batchEpoch();

        void bons$batchEpoch(int epoch);

        /** Server thread (batch owner) only: batched packets were handed over since this connection's last flush. */
        boolean bons$unflushed();

        void bons$unflushed(boolean value);
    }

    /** The thread with an open batch, or null; only that thread reads or writes the fields below. */
    private static volatile Thread owner;
    private static int depth;
    private static Connection only;
    private static Connection marked;
    private static int epoch = 1;
    private static final ArrayList<Connection> TOUCHED = new ArrayList<>();
    private static volatile boolean announced;

    private FlushBatch() {
    }

    /**
     * Opens a batch on this thread (connection = null: all connections; else that connection only). Returns false when
     * the batch is not this call's to close (switch off, or another batch is open, on this or another thread).
     */
    public static boolean begin(Connection connection) {
        if (!enabled || !channelMover().isEmpty()) return false;
        Thread self = Thread.currentThread();
        synchronized (FlushBatch.class) {
            if (owner != null) {
                if (owner == self) depth++;
                return owner == self;
            }
            owner = self;
            depth = 1;
            only = connection;
            return true;
        }
    }

    /**
     * 1.0.36: other mods' mixins known to move a Connection's channel to another event loop. Only applied mixins count:
     * Mixin leaves @MixinMerged on every member it merges into Connection, so a mixin a mod's plugin skipped is not seen.
     */
    static final Set<String> CHANNEL_MOVERS = Set.of("com.ishland.vmp.mixins.networking.eventloops.MixinClientConnection");
    /** null = not checked yet; "" = no other mod moves connections between event loops; else why this switch stands down. */
    private static volatile String moverCheck;

    /** "" when the batches may run, else why the switch stands down (checked once, on the first batch; one INFO line). */
    public static String channelMover() {
        String r = moverCheck;
        if (r == null) {
            synchronized (FlushBatch.class) {
                r = moverCheck;
                if (r == null) {
                    r = findChannelMover(Connection.class);
                    moverCheck = r;
                    if (!r.isEmpty())
                        LOGGER.info("Bons and Furious: vanilla_connection_flush_batching stands down: {}; every packet is sent as Minecraft sends it", r);
                }
            }
        }
        return r;
    }

    /**
     * Public for the offline proof: "" when no other mod's mixin merged into the given (Connection) class moves its channel
     * between event loops, else why not. A mover is a known one (CHANNEL_MOVERS) or a mixin whose class file, without
     * debug information, names Netty's deregister (a channel must be deregistered from one loop to be registered with
     * another). A mixin class file that cannot be read, or a failing check, counts as a mover (stand down).
     */
    public static String findChannelMover(Class<?> connection) {
        try {
            TreeSet<String> foreign = new TreeSet<>();
            for (Method m : connection.getDeclaredMethods()) {
                MixinMerged merged = m.getAnnotation(MixinMerged.class);
                if (merged != null && !merged.mixin().startsWith("bons.furious.") && !merged.mixin().startsWith("bons.pure.")
                        && !merged.mixin().startsWith("agentcraft.")) foreign.add(merged.mixin());
            }
            ClassLoader loader = connection.getClassLoader() != null ? connection.getClassLoader() : ClassLoader.getSystemClassLoader();
            for (String mixin : foreign) {
                if (CHANNEL_MOVERS.contains(mixin))
                    return mixin + " moves connections to other network threads when their protocol changes, and a burst's flush could then run before writes still queued on the old thread";
                byte[] bytes;
                try (InputStream in = loader.getResourceAsStream(mixin.replace('.', '/') + ".class")) {
                    bytes = in == null ? null : in.readAllBytes();
                }
                if (bytes == null) return "the class file of Connection mixin " + mixin + " could not be read";
                org.objectweb.asm.ClassReader reader = new org.objectweb.asm.ClassReader(bytes);
                org.objectweb.asm.ClassWriter writer = new org.objectweb.asm.ClassWriter(0);
                reader.accept(writer, org.objectweb.asm.ClassReader.SKIP_DEBUG);
                if (new String(writer.toByteArray(), StandardCharsets.ISO_8859_1).contains("deregister"))
                    return "Connection mixin " + mixin + " deregisters channels (moves connections between network threads), and a burst's flush could then run before writes still queued on the old thread";
            }
            return "";
        } catch (Throwable t) {
            return "the check of other mods' Connection mixins failed (" + t + ")";
        }
    }

    /** Closes the batch begin returned true for; at the outermost end every connection with batched packets is flushed. */
    public static void end() {
        if (--depth > 0) return;
        try {
            for (int i = 0; i < TOUCHED.size(); i++) {
                Connection c = TOUCHED.get(i);
                Member m = (Member) c;
                if (!m.bons$unflushed()) continue;
                m.bons$unflushed(false);
                Channel ch = c.channel();
                if (ch != null) {
                    ch.flush();
                    FLUSHES.incrementAndGet();
                }
            }
        } finally {
            TOUCHED.clear();
            epoch++;
            marked = null;
            only = null;
            synchronized (FlushBatch.class) {
                owner = null;
            }
        }
    }

    /**
     * Connection.send, before sendPacket(packet, listener): whether this packet is handed over batched. Only on the batch
     * thread, without a listener, to a connection the batch covers, and never a keep-alive.
     */
    public static boolean mark(Connection connection, Packet<?> packet) {
        if (owner != Thread.currentThread() || (only != null && only != connection) || packet instanceof ClientboundKeepAlivePacket) return false;
        marked = connection;
        return true;
    }

    public static void unmark() {
        if (owner == Thread.currentThread()) marked = null;
    }

    /**
     * Connection.sendPacket, instead of eventLoop.execute(task): true when the task was queued batched (lazyExecute, no
     * wakeup); false means the caller runs Minecraft's execute.
     */
    public static boolean handOver(Connection connection, Object eventLoop, Runnable task) {
        if (marked != connection || owner != Thread.currentThread() || !(eventLoop instanceof AbstractEventExecutor executor)) return false;
        marked = null;
        Member m = (Member) connection;
        if (m.bons$batchEpoch() != epoch) {
            m.bons$batchEpoch(epoch);
            TOUCHED.add(connection);
        }
        m.bons$unflushed(true);
        executor.lazyExecute(new BatchedWrite(connection, task));
        BATCHED.incrementAndGet();
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: vanilla_connection_flush_batching applies (packet bursts of the entity tracker, chunk broadcast and a player's own tick are written without waking the network thread per packet and flushed once per burst)");
        }
        return true;
    }

    /**
     * 1.0.34: Connection.disconnect, before it closes the channel. Netty drops every write that was not flushed when a
     * channel closes, and Minecraft sends a kick reason without a send listener right before closing (the login listener's
     * disconnect from its tick: ban, whitelist, full server, slow login). On the batch thread, a connection with batched
     * packets is flushed here: the flush is queued behind their writes and ahead of the close, so they leave before it, as
     * each of Minecraft's writeAndFlush calls made them leave. Another thread closing a connection while the batch thread
     * has a batch open gets one plain flush first (its writes may be queued; an earlier flush never changes a byte); with
     * no batch open, or for connections without batched packets, nothing happens.
     */
    public static void beforeDisconnect(Connection connection) {
        if (owner != Thread.currentThread()) {
            if (owner != null) {
                Channel ch = connection.channel();
                if (ch != null) {
                    ch.flush();
                    DISCONNECT_FLUSHES.incrementAndGet();
                }
            }
            return;
        }
        Member m = (Member) connection;
        if (!m.bons$unflushed()) return;
        m.bons$unflushed(false);
        Channel ch = connection.channel();
        if (ch != null) {
            ch.flush();
            DISCONNECT_FLUSHES.incrementAndGet();
        }
    }

    /** Runs Minecraft's send task on the event loop, with that packet's write not flushed. */
    static final class BatchedWrite implements Runnable {
        private final Connection connection;
        private final Runnable task;

        BatchedWrite(Connection connection, Runnable task) {
            this.connection = connection;
            this.task = task;
        }

        @Override
        public void run() {
            Channel ch = this.connection.channel();
            if (ch != null && ch.bytesBeforeUnwritable() < GUARD_BYTES) {
                ch.flush();
                GUARD_FLUSHES.incrementAndGet();
            }
            Member m = (Member) this.connection;
            m.bons$writeOnly(true);
            try {
                this.task.run();
            } finally {
                m.bons$writeOnly(false);
            }
        }
    }
}
