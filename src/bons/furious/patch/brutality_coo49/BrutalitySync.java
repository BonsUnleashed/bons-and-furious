package bons.furious.patch.brutality_coo49;

import bons.furious.mixin.brutality_coo49.BrutalityCoo49ChunkMapAccessor;
import bons.furious.mixin.brutality_coo49.BrutalityCoo49TrackedEntityAccessor;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import net.goo.brutality.network.ClientboundSyncCapabilitiesPacket;
import net.goo.brutality.network.PacketHandler;
import net.goo.brutality.registry.BrutalityModMobEffects;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerPlayerConnection;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.simple.SimpleChannel;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switches brutality_effect_sync_own_effects_only (kind change, off by default) and
 * brutality_effect_sync_tracking_scope (exact variant, on) (Brutality 0.8.5, MIT; server; since 1.0.49). Ideas: Collections
 * Of Optimizations (brutality.effectSyncOwnEffectsOnly, brutality.skipUnwatchedEffectSync), idea text only.
 *
 * ForgeEffectSyncHandler's MobEffectEvent Added / Remove / Expired handlers run for every effect event of every entity on a
 * server level (Added fires on every addEffect call, even one that changes nothing). Their capability lambdas set one of
 * four flags (only for Brutality's own effects MIRACLE_BLIGHT, ENRAGED, THE_VOID, LIGHT_BOUND), then build a
 * ClientboundSyncCapabilitiesPacket of all nine synced capabilities of the entity and send it with PacketDistributor.ALL to
 * every online player. A client acts on it only when its level holds the entity (ClientAccess.syncCapabilities returns when
 * level.getEntity(id) is null).
 *
 * brutality_effect_sync_tracking_scope (exact variant): on the server thread the packet goes to the players whose clients
 * hold the entity, as each of them gets it from stock: the connections in the entity's ChunkMap TrackedEntity.seenBy whose
 * player is in the PlayerList, plus the entity itself when it is a ServerPlayer in the PlayerList (sent directly: a player
 * has no tracker entry of its own while it logs in). The message is encoded once by Brutality's own channel (the packet
 * PacketDistributor.ALL builds) and each recipient's connection gets that packet. With no recipient the packet is not even
 * built. The one difference: no packet to a player whose client does not hold the entity (it drops it unread: a client
 * holds a server entity only while it is in seenBy, or is that player; spawn and remove packets come from the tracker on
 * the same connection, in order, and entity ids are unique on a server). Off the server thread (an effect added during
 * world generation) the stock send runs.
 *
 * brutality_effect_sync_own_effects_only (trade-off, off by default): the packet is built and sent only for events of
 * Brutality's four own effects; the flag setters run as shipped. Difference: star, sticky bomb, rage, mana and combo values
 * that Brutality changes without a sync of their own reach clients at the next Brutality effect event, not with the next
 * effect event of any kind (client display only; nothing saved changes).
 *
 * Flags (emergency off): -Dbons_and_furious.brutalityEffectSyncTrackingScope=false,
 * -Dbons_and_furious.brutalityEffectSyncOwnEffectsOnly=false.
 */
public final class BrutalitySync {
    public static final String KEY_SCOPE = "brutality_effect_sync_tracking_scope";
    public static final String KEY_OWN = "brutality_effect_sync_own_effects_only";
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean scopeEnabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.brutalityEffectSyncTrackingScope", "true"));
    public static volatile boolean ownEnabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.brutalityEffectSyncOwnEffectsOnly", "true"));
    private static volatile SimpleChannel channel;
    private static volatile boolean channelLooked, scopeAnnounced, ownAnnounced;

    private BrutalitySync() {
    }

    // ------------------------------------------------------------------------------------------------- own effects only
    /** True for Brutality's four own effects (the same registry objects the lambda compares with, in the same order). */
    public static boolean ownEffect(MobEffect effect) {
        return effect == BrutalityModMobEffects.MIRACLE_BLIGHT.get() || effect == BrutalityModMobEffects.ENRAGED.get()
                || effect == BrutalityModMobEffects.THE_VOID.get() || effect == BrutalityModMobEffects.LIGHT_BOUND.get();
    }

    /** The wrapped `new ClientboundSyncCapabilitiesPacket(id, entity)` (own effects only): null = not built, not sent. */
    public static ClientboundSyncCapabilitiesPacket ownBuild(int id, Entity entity, Operation<ClientboundSyncCapabilitiesPacket> original, MobEffect effect) {
        if (ownEnabled && !ownEffect(effect)) {
            if (!ownAnnounced) {
                ownAnnounced = true;
                LOGGER.info("Bons and Furious: {} applies (Brutality syncs its capabilities to clients for its own four effects only)", KEY_OWN);
            }
            return null;
        }
        return original.call(id, entity);
    }

    /** The wrapped PacketHandler.sendToAllClients(msg): a packet that was not built is not sent. */
    public static void sendBuilt(Object msg, Operation<Void> original) {
        if (msg != null) original.call(msg);
    }

    // ------------------------------------------------------------------------------------------------- tracking scope
    /** The wrapped `new ClientboundSyncCapabilitiesPacket(id, entity)` (tracking scope): not built when nobody holds the entity. */
    public static ClientboundSyncCapabilitiesPacket scopeBuild(int id, Entity entity, Operation<ClientboundSyncCapabilitiesPacket> original) {
        if (scopeEnabled && entity != null && entity.m_9236_() instanceof ServerLevel level && level.m_7654_().m_18695_() && channel() != null
                && recipients(level, entity).isEmpty()) {
            announceScope();
            return null;
        }
        return original.call(id, entity);
    }

    /** The wrapped PacketHandler.sendToAllClients(msg) (tracking scope). */
    public static void scopeSend(Object msg, Operation<Void> original, Entity entity) {
        if (msg == null) return;
        if (scopeEnabled && entity != null && entity.m_9236_() instanceof ServerLevel level && level.m_7654_().m_18695_()) {
            SimpleChannel ch = channel();
            if (ch != null) {
                List<ServerPlayerConnection> to = recipients(level, entity);
                if (!to.isEmpty()) {
                    Packet<?> packet = ch.toVanillaPacket(msg, NetworkDirection.PLAY_TO_CLIENT);
                    for (ServerPlayerConnection c : to) c.m_9829_(packet);
                }
                announceScope();
                return;
            }
        }
        original.call(msg);
    }

    /** The connections whose clients hold the entity and that stock's PacketDistributor.ALL reaches (see the class comment). */
    public static List<ServerPlayerConnection> recipients(ServerLevel level, Entity entity) {
        PlayerList list = level.m_7654_().m_6846_();
        List<ServerPlayerConnection> out = new ArrayList<>(4);
        Object tracked = ((BrutalityCoo49ChunkMapAccessor) level.m_7726_().f_8325_).bons$brutalityCoo49EntityMap().get(entity.m_19879_());
        if (tracked != null) {
            for (ServerPlayerConnection c : ((BrutalityCoo49TrackedEntityAccessor) tracked).bons$brutalityCoo49SeenBy()) {
                ServerPlayer p = c.m_142253_();
                if (p != null && list.m_11259_(p.m_20148_()) == p) out.add(c);
            }
        }
        if (entity instanceof ServerPlayer self && self.f_8906_ != null && list.m_11259_(self.m_20148_()) == self) out.add(self.f_8906_);
        return out;
    }

    /** Brutality's own channel (PacketHandler.NETWORK_CHANNEL, private), looked up once; null = the stock send runs. */
    static SimpleChannel channel() {
        if (!channelLooked) {
            synchronized (BrutalitySync.class) {
                if (!channelLooked) {
                    try {
                        Field f = PacketHandler.class.getDeclaredField("NETWORK_CHANNEL");
                        f.setAccessible(true);
                        channel = (SimpleChannel) f.get(null);
                    } catch (Throwable t) {
                        LOGGER.warn("Bons and Furious: {} cannot reach Brutality's network channel ({}); capability syncs go to every player as shipped", KEY_SCOPE, t.toString());
                    }
                    channelLooked = true;
                }
            }
        }
        return channel;
    }

    private static void announceScope() {
        if (!scopeAnnounced) {
            scopeAnnounced = true;
            LOGGER.info("Bons and Furious: {} applies (Brutality's capability syncs go to the players whose clients hold the entity)", KEY_SCOPE);
        }
    }
}
