package bons.furious.patch.network_flush;

import com.mojang.logging.LogUtils;
import io.netty.channel.Channel;
import io.netty.util.concurrent.AbstractEventExecutor;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundKeepAlivePacket;
import org.slf4j.Logger;

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
 * stream cipher see the same sequence), and the bytes leave in fewer socket writes before the tick ends. Connection.tick
 * ends with Minecraft's own flush, which serves as that connection's end of burst.
 *
 * Unchanged (Minecraft's path): packets with a send listener (disconnects, ...), keep-alive packets, packets that switch
 * the protocol (doSendPacket writes and flushes them), packets sent from any other thread or outside a burst, packets to
 * other connections during a connection's own tick, packets queued before a connection was active (flushQueue), and
 * event loops that are not Netty AbstractEventExecutors. Other threads' packets still go through execute and
 * writeAndFlush, which also flushes the batched packets queued before them, so per connection the bytes are identical.
 * To keep Netty's unflushed bytes small, a batched write first flushes when the connection is within 32 KB of Netty's
 * high-water mark (64 KB) - a flush earlier than the burst end, never a different byte.
 *
 * -Dbons_and_furious.connectionFlushBatching=false: Minecraft's path for every packet.
 */
public final class FlushBatch {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.connectionFlushBatching", "true"));
    /** Counters for rigs: packets handed over without a wakeup, end-of-burst flushes, early flushes near the water mark. */
    public static final AtomicLong BATCHED = new AtomicLong(), FLUSHES = new AtomicLong(), GUARD_FLUSHES = new AtomicLong();
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
        if (!enabled) return false;
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

    /** Connection.tick's own flush (the end of that connection's burst): nothing left to flush for it. */
    public static void flushedByMinecraft(Connection connection) {
        if (owner == Thread.currentThread()) ((Member) connection).bons$unflushed(false);
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
