package bons.furious.patch.stale_player_maps;

import bons.furious.patch.stale_player_maps.StalePlayerKeys.Kind;
import bons.furious.patch.stale_player_maps.StalePlayerKeys.Side;
import com.becks.uniquedungeons.common.stackCooldowns.StackCooldownUtils;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.world.entity.Entity;

/**
 * Bons and Furious fixes stale_player_map_purge_uniquedungeons (both sides) and stale_player_map_purge_uniquedungeons_remote
 * (client only), Unique Dungeons 0.70.3.2 (mods.toml: 0.70.3), MIT. Ours; no Unique Dungeons code is carried.
 *
 * StackCooldownMap keeps one StackCooldowns per player in a static HashMap that only grows; getCooldowns (the map's only
 * accessor, from the Player.tick mixin, the GUI, the item and the cooldown packet) fills it for every player object on both
 * sides. In single player the client and the integrated server share the class, so two threads read and write the same
 * HashMap without a lock (a stock race: lost or corrupted entries, ConcurrentModificationException).
 *
 * stale_player_map_purge_uniquedungeons (guarded by members both sides have):
 *  - getCooldowns now runs whole under one lock (MixinExtras @WrapMethod): same calls, same order, same answers for one
 *    thread; two threads take turns;
 *  - the map is handed to {@link StalePlayerKeys} as a both-side ID map with that lock: logged-out players' entries go on a
 *    dedicated server, a joining player takes over an equal (same-id) key, and a client session's entries go when it ends.
 * Note: an entry's ServerStackCooldowns holds the ServerPlayer it was first made for; a respawned player keeps using it (stock
 * behaviour, unchanged), so that first ServerPlayer stays reachable until its player logs out.
 *
 * stale_player_map_purge_uniquedungeons_remote (client only; its own key because its premise rests on a client-only member,
 * StackCooldownUtils.updateClientCooldowns, which Forge strips on a dedicated server - while one key guarded it, that key
 * never applied on a dedicated server): on a client a remote player's StackCooldowns never gets a cooldown (addCooldown is
 * called only from RageBlade.hurtEnemy, which runs where the hit lands - the server - and from updateClientCooldowns for the
 * local player), and an empty StackCooldowns answers every question (isOnCooldown, getCooldownPercent) exactly as a fresh
 * one: its tick counter is only ever compared with cooldowns added later. So a departed remote player's entry may go at
 * once. The key's mixin (a marker interface on StackCooldownUtils, {@link RemoteInert}) applies only when every premise
 * member matches its fingerprint; the purge asks for it here.
 *
 * Runtime flags: -Dbons_and_furious.stalePlayerMapPurgeUniquedungeons=false runs getCooldowns without the lock and leaves the
 * map alone; -Dbons_and_furious.stalePlayerMapPurgeUniquedungeonsRemote=false keeps departed remote players' entries (until a
 * key swap or the session end). Shadow mode: -Dbons_and_furious.stalePlayerMapPurgeUniquedungeons.shadow=true checks every
 * getCooldowns call against the server ids purged at logout: a lookup for one would be an answer stock gives differently
 * (SHADOW_MISMATCHES, WARN for the first 20; it must stay 0).
 */
public final class UniqueDungeonsMaps {
    public static final String KEY = "stale_player_map_purge_uniquedungeons";
    public static final String REMOTE_KEY = "stale_player_map_purge_uniquedungeons_remote";
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.stalePlayerMapPurgeUniquedungeons", "true"));
    public static volatile boolean remoteEnabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.stalePlayerMapPurgeUniquedungeonsRemote", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.stalePlayerMapPurgeUniquedungeons.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong();
    public static final AtomicLong SHADOW_MISMATCHES = new AtomicLong();
    /** The lock every getCooldowns call and every change we make to the map run under. */
    public static final Object LOCK = new Object();
    private static volatile Boolean remoteApplied;

    /** Added to StackCooldownUtils by stale_player_map_purge_uniquedungeons_remote's mixin (client only, guarded). */
    public interface RemoteInert {
    }

    private UniqueDungeonsMaps() {
    }

    /** From StackCooldownMap.<clinit> (our mixin). */
    public static void track(Map<?, ?> map) {
        StalePlayerKeys.track(KEY, "Unique Dungeons StackCooldownMap.map", map, Kind.ID, Side.BOTH, LOCK, () -> enabled, UniqueDungeonsMaps::remoteInert);
    }

    /** Client only (asked from client events): may a departed remote player's entry go at once? */
    static boolean remoteInert() {
        if (!remoteEnabled) return false;
        Boolean applied = remoteApplied;
        if (applied == null) {
            boolean a;
            try {
                a = RemoteInert.class.isAssignableFrom(StackCooldownUtils.class);
            } catch (Throwable t) {
                a = false;
            }
            remoteApplied = applied = a;
            if (a) StalePlayerKeys.LOGGER.info("Bons and Furious: {} applies (departed remote players' empty cooldown records leave the client map at once)", REMOTE_KEY);
        }
        return applied;
    }

    /** The @WrapMethod body for StackCooldownMap.getCooldowns(player). */
    public static <T> T getCooldowns(Object player, Operation<T> original) {
        if (!enabled) return original.call(player);
        if (SHADOW && player instanceof Entity e && !e.m_9236_().m_5776_()) {
            SHADOW_CHECKS.incrementAndGet();
            if (StalePlayerKeys.RETIRED_SERVER_IDS.contains(e.m_19879_()) && SHADOW_MISMATCHES.incrementAndGet() <= 20)
                StalePlayerKeys.LOGGER.warn("Bons and Furious: {} shadow mismatch: id {} was purged at logout and is asked for again", KEY, e.m_19879_());
        }
        synchronized (LOCK) {
            return original.call(player);
        }
    }
}
