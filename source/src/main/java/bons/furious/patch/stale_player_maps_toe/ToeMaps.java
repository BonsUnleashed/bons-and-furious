package bons.furious.patch.stale_player_maps_toe;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Map;
import java.util.function.BooleanSupplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious fix stale_player_map_purge_tonsofenchants (Tons Of Enchants 1.21.1-v1.0 for NeoForge, MIT; server).
 * Ours; no Tons of Enchants code is carried. Idea: Collections Of Optimizations (vanilla.purgeStalePlayerMaps), idea text
 * only.
 *
 * Tons Of Enchants keeps two static HashMap<Player, Boolean> in its helper class PU: excavatorMap ("is this player
 * excavating"), written by PU.destroyBlocksCube (put true, put false) and read by the Excavator BreakEvent handler
 * (OtherEnchantmentEvents.excavator: getOrDefault), and orechainMap ("is this player breaking an ore vein"), read and
 * written by PU.destroyOreVein (getOrDefault, put true, put false) and read by OtherEnchantmentEvents.orechain. Nothing
 * ever removes a key, so every player who ever excavated or chained ore stays in the server's memory (inventory,
 * connection, advancements) until a restart, and after a respawn the old player object stays the key. Both maps are
 * handed to the shared layer of the stale_player_map_purge_* fixes (StalePlayerKeys in the stale_player_maps group,
 * reached by name so this group builds on its own) as server ID maps: when a player object joins a level the key that
 * equals it (same entity id: a respawned player) is swapped to the new object with the same value, and a player's entry
 * goes when it leaves with UNLOADED_WITH_PLAYER (logout, server stop; server entity ids are never handed out again).
 * BlockEvent.BreakEvent fires only on the server (ServerPlayerGameMode.destroyBlock), so only the server's maps are used.
 *
 * Why every answer stays the same: HashMap compares Player keys with Entity.equals / hashCode (the entity id). A swap keeps
 * the key's id and value; a purged id can never be asked again. Players who can still break blocks get the same
 * getOrDefault answers, so Excavator and Ore Chain do the same thing.
 *
 * -Dbons_and_furious.stalePlayerMapPurgeTonsofenchants=false leaves the maps alone. If the shared layer cannot be reached,
 * one WARN and the maps stay as Tons of Enchants keeps them.
 */
public final class ToeMaps {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static final String KEY = "stale_player_map_purge_tonsofenchants";
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.stalePlayerMapPurgeTonsofenchants", "true"));
    /** For the harness: whether the maps reached the shared layer. */
    public static volatile boolean tracked;

    private ToeMaps() {
    }

    /** From PU.<clinit> RETURN (our mixin): hand both maps over. Never throws (it runs in a static initializer). */
    public static void track(Map<?, ?> excavating, Map<?, ?> chaining) {
        try {
            ClassLoader cl = ToeMaps.class.getClassLoader();
            Class<?> layer = Class.forName("bons.furious.patch.stale_player_maps.StalePlayerKeys", true, cl);
            @SuppressWarnings({"unchecked", "rawtypes"})
            Class<Enum> kind = (Class<Enum>) Class.forName("bons.furious.patch.stale_player_maps.StalePlayerKeys$Kind", true, cl);
            @SuppressWarnings({"unchecked", "rawtypes"})
            Class<Enum> side = (Class<Enum>) Class.forName("bons.furious.patch.stale_player_maps.StalePlayerKeys$Side", true, cl);
            MethodHandle track = MethodHandles.publicLookup().findStatic(layer, "track",
                    MethodType.methodType(void.class, String.class, String.class, Map.class, kind, side, Object.class, BooleanSupplier.class));
            @SuppressWarnings("unchecked")
            Object id = Enum.valueOf(kind, "ID");
            @SuppressWarnings("unchecked")
            Object server = Enum.valueOf(side, "SERVER");
            BooleanSupplier on = () -> enabled;
            track.invoke(KEY, "Tons Of Enchants PU.excavatorMap", excavating, id, server, null, on);
            track.invoke(KEY, "Tons Of Enchants PU.orechainMap", chaining, id, server, null, on);
            tracked = true;
        } catch (Throwable t) {
            LOGGER.warn("Bons and Furious: {} cannot reach the shared player-map layer ({}); Tons of Enchants' player maps stay as the mod keeps them", KEY, t.toString());
        }
    }
}
