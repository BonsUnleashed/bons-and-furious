package bons.furious.patch.storagedrawers_sync_c2;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.logging.LogUtils;
import com.texelsaurus.minecraft.chameleon.network.ChameleonPacket;
import com.texelsaurus.minecraft.chameleon.service.ChameleonNetworking;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import org.slf4j.Logger;

/**
 * Bons and Furious switch storagedrawers_count_sync_holders (Storage Drawers, MIT; 1.21.1 tested build:
 * StorageDrawers-neoforge-1.21.1-13.11.4; server side, which in singleplayer is the integrated server). No Storage Drawers
 * code is carried here.
 *
 * Every change of a drawer's stored amount (a hopper or pipe tick, every slot of a shift-click deposit) sends Storage
 * Drawers' CountUpdateMessage to every player of the dimension within 500 blocks (ChameleonNetworking.sendToPlayersNear ->
 * NeoForge PacketDistributor.sendToPlayersNear -> PlayerList.broadcast). The client looks the drawer's block entity up and
 * does nothing when it does not hold that chunk (ClientChunkCache answers with its empty chunk), so every recipient that
 * cannot hold the chunk costs a server-thread hand-off, an encode and the bytes on the wire for nothing. A client holds a
 * chunk only after the server sent it, and the server sends "forget" in the same call that takes the chunk out of the
 * player's tracking view, so every client that can hold the chunk is in ChunkMap.getPlayers(chunk, false), the set vanilla
 * itself sends block and block-entity updates to.
 *
 * What changes: while one of Storage Drawers' two count sends runs (standard drawers' syncClientCount, compacting drawers'
 * GroupData.onAmountChanged), PlayerList.broadcast's per-player send of that packet (a custom payload of type
 * storagedrawers:count_update) skips a player who is not in getPlayers(drawer chunk, false). Everything else is the
 * original: the dimension test, the 500-block sphere, other mods' changes to broadcast, the encoded bytes, the order, the
 * tick. Recipients = original recipients that can hold the chunk; every skipped packet was one the client would have
 * discarded. Nothing is cached. When the drawer's dimension has at most one player the send is not touched at all (see send).
 *
 * Ported to 1.21.1: Chameleon's NeoForge service (NeoforgeNetworking) sends through PacketDistributor.sendToPlayersNear,
 * which wraps the payload in one ClientboundCustomPayloadPacket and calls the same PlayerList.broadcast loop; there is no
 * SimpleChannel any more, so the packet is recognised by its payload type id (CountUpdateMessage.TYPE,
 * storagedrawers:count_update) instead of the channel name storagedrawers:main. 1.21.1's getPlayers(chunk, false) also
 * leaves out a player whose copy of the chunk is still queued (PlayerChunkSender.isPending): that client does not hold the
 * chunk yet, and the chunk packet built later carries the current counts, so the skip is still one the client would have
 * discarded. The client side (CountUpdateMessage.handleMessage -> CountUpdateMessageHandler.handle: getBlockEntity, nothing
 * for an empty chunk) is unchanged.
 */
public final class CountSyncHolders {
    /** Runtime switch. -Dbons_and_furious.storagedrawersCountSyncHolders=false turns it off. */
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.storagedrawersCountSyncHolders", "true"));
    /** Count sends seen, recipients kept and recipients skipped (tests and the rig). */
    public static final AtomicLong SENDS = new AtomicLong(), KEPT = new AtomicLong(), SKIPPED = new AtomicLong();
    /** Storage Drawers' count update payload type (CountUpdateMessage.TYPE). */
    public static final ResourceLocation PAYLOAD = ResourceLocation.fromNamespaceAndPath("storagedrawers", "count_update");
    private static final Logger LOGGER = LogUtils.getLogger();
    private static volatile boolean announced;

    /** The count send running on its owner thread; a plain field: only the owner writes it and acts on it. */
    private static Scope current;

    private CountSyncHolders() {
    }

    static final class Scope {
        final Thread owner = Thread.currentThread();
        final ServerLevel level;
        final int chunkX, chunkZ;
        List<ServerPlayer> holders;      // computed at the first recipient

        Scope(ServerLevel level, int chunkX, int chunkZ) {
            this.level = level;
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
        }
    }

    /**
     * Wraps Storage Drawers' sendToPlayersNear call: the original send runs with this drawer's holder filter set. A dimension
     * with at most one player (singleplayer, a lone player there, one player online) has at most one recipient, so there is
     * nothing worth checking: the send runs exactly as stock, without the scope. Sending to a player who cannot hold the
     * chunk is what stock does and the client discards it, so either path gives the result the switch promises; the
     * player count only decides whether the check is worth its cost.
     */
    public static void send(ChameleonNetworking network, ChameleonPacket packet, ServerLevel level, double x, double y, double z,
                            double radius, Operation<Void> original) {
        if (!enabled || level == null || current != null || level.players().size() <= 1) {
            original.call(network, packet, level, x, y, z, radius);
            return;
        }
        Scope s = new Scope(level, Mth.floor(x) >> 4, Mth.floor(z) >> 4);
        current = s;
        SENDS.incrementAndGet();
        try {
            original.call(network, packet, level, x, y, z, radius);
        } finally {
            current = null;
        }
    }

    /** PlayerList.broadcast's send to one player: false only for Storage Drawers' count packet to a player who cannot hold the chunk. */
    public static boolean keep(ServerGamePacketListenerImpl connection, Packet<?> packet) {
        Scope s = current;
        if (s == null || s.owner != Thread.currentThread() || !(packet instanceof ClientboundCustomPayloadPacket p)) {
            return true;
        }
        CustomPacketPayload payload = p.payload();
        CustomPacketPayload.Type<?> type = payload == null ? null : payload.type();
        if (type == null || !PAYLOAD.equals(type.id())) {
            return true;
        }
        List<ServerPlayer> holders = s.holders;
        if (holders == null) {
            holders = s.holders = s.level.getChunkSource().chunkMap.getPlayers(new ChunkPos(s.chunkX, s.chunkZ), false);
        }
        if (holders.contains(connection.player)) {
            KEPT.incrementAndGet();
            return true;
        }
        SKIPPED.incrementAndGet();
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: storagedrawers_count_sync_holders: drawer count updates go only to players whose client can hold "
                    + "the drawer's chunk (first skipped for chunk [{}, {}])", s.chunkX, s.chunkZ);
        }
        return false;
    }
}
