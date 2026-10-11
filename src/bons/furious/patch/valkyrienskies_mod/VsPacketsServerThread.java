package bons.furious.patch.valkyrienskies_mod;

import com.mojang.logging.LogUtils;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.slf4j.Logger;
import org.valkyrienskies.core.internal.hooks.VsiCoreHooksIn;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

/**
 * Bons and Furious switch valkyrien_packets_server_thread (Valkyrien Skies 2.4.11 on Forge 47.4.16, both sides; acts on
 * the side that receives a player's packets: the dedicated or the integrated server). SRG member names.
 *
 * Valkyrien Skies 2.4.x handles every packet a player's client sends it on the Netty thread that received it: its Forge
 * channel consumer calls Valkyrien Skies core's onReceiveServer directly, and core runs the packet's handlers in place.
 * The handler of the standing-on-a-ship packet (PacketPlayerShipMotion) calls ServerPlayer.setPos with the ship-to-world
 * position whenever the player's last vanilla move packet has been handled; the one for a ridden entity
 * (PacketEntityShipMotion) calls Entity.setPos. Entity.setPosRaw clears the cached feet block state and, when the
 * position crosses into another 16-block section, moves the entity between the level's entity sections
 * (EntitySectionStorage's hash map and sorted key set). Minecraft only ever does that on the server thread, so a move
 * from the Netty thread races the server thread. On a test server, always with a player on or beside a ship: Radium's
 * experimental mob-cap walk over that map failed with "this.wrapped" is null (2026-10-07, 10-09, 10-10),
 * Entity.getFeetBlockState returned null between its check and its return (a "Ticking player" kick in
 * ForgeHooks.isLivingOnLadder, 2026-10-10), and the player's section lost track of it ("wasn't found in section",
 * 2026-03-21).
 *
 * Here a packet from a player is not handled on the receiving thread. Its payload is copied (Forge releases the
 * original buffer when the consumer returns) and the same onReceiveServer call is queued with executeIfPossible, the
 * call Minecraft's own PacketUtils.ensureRunningOnSameThread uses for its packets: the same task queue, so a Valkyrien
 * Skies packet still runs after the vanilla move packet the client sent before it, and Valkyrien Skies'
 * handledMovePacket / queued-position handshake works as designed. Like Minecraft's packets, a packet whose player
 * has disconnected by the time it runs is dropped, and so is one that arrives while the server shuts down. A handler
 * that throws is logged (the first 5 times) instead of closing the connection from the Netty thread.
 *
 * Unchanged: packets the client receives (no sender), a call that already runs on the server thread, and everything
 * Valkyrien Skies sends.
 */
public final class VsPacketsServerThread {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final AtomicBoolean ANNOUNCED = new AtomicBoolean();
    private static final AtomicLong QUEUED = new AtomicLong();
    private static final AtomicLong DROPPED = new AtomicLong();
    private static final AtomicLong FAILED = new AtomicLong();

    private VsPacketsServerThread() {
    }

    /** Queues the packet on the player's server thread; false = leave it to Valkyrien Skies' own path. */
    public static boolean defer(VsiCoreHooksIn hooks, ByteBuf payload, ServerPlayer sender) {
        MinecraftServer server = sender.f_8924_;
        if (server == null || server.m_18695_()) {          // m_18695_ = isSameThread: already where it belongs
            return false;
        }
        ByteBuf copy = Unpooled.copiedBuffer(payload);
        if (ANNOUNCED.compareAndSet(false, true)) {
            LOGGER.info("Bons and Furious: valkyrien_packets_server_thread runs Valkyrien Skies' player packets on the server thread "
                    + "(first one from {}, received on {})", sender.m_6302_(), Thread.currentThread().getName());
        }
        try {
            server.m_201446_(() -> run(server, hooks, copy, sender));    // m_201446_ = executeIfPossible
            QUEUED.incrementAndGet();
        } catch (RejectedExecutionException e) {             // the server is shutting down
            DROPPED.incrementAndGet();
        }
        return true;
    }

    private static void run(MinecraftServer server, VsiCoreHooksIn hooks, ByteBuf copy, ServerPlayer sender) {
        ServerGamePacketListenerImpl connection = sender.f_8906_;
        // executeIfPossible runs a task in place if the server stopped between its check and the queue: never handle
        // that one off the server thread. m_9232_ = hasDisconnected, m_6198_ = isAcceptingMessages.
        if (!server.m_18695_() || sender.m_9232_() || connection == null || !connection.m_6198_()) {
            DROPPED.incrementAndGet();
            return;
        }
        try {
            hooks.onReceiveServer(copy, VSGameUtilsKt.getPlayerWrapper(sender));
        } catch (RuntimeException e) {
            long n = FAILED.incrementAndGet();
            if (n <= 5) {
                LOGGER.error("Bons and Furious: valkyrien_packets_server_thread: Valkyrien Skies could not handle a packet from {} "
                        + "(failure {}); it was dropped", sender.m_6302_(), n, e);
            }
        }
    }

    /** Packets queued, dropped (player gone or server stopping) and failed so far (for tests). */
    public static long[] counts() {
        return new long[]{QUEUED.get(), DROPPED.get(), FAILED.get()};
    }
}
