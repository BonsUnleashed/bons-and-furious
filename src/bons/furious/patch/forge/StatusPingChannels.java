package bons.furious.patch.forge;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;

/**
 * Bons and Furious switch forge_status_ping_channel_groups (Forge 47.4.16).
 *
 * ServerStatusPing.toBuf writes the server-list ping's mod list. For every mod it calls getChannelsForMod, which streams
 * over the whole channel map and keeps that mod's namespace: mods x channels steps per ping (about 490 mod ids here). At
 * the start of toBuf the channels are now grouped by namespace in one pass over the same map, each group in the map's
 * iteration order (the order the stream's filter keeps), and toBuf's getChannelsForMod calls take their group from it
 * (an empty list for a mod without channels). The groups belong to that one toBuf call on that thread and are dropped at
 * its return; the bytes written are the same.
 */
public final class StatusPingChannels {
    /** Runtime switch (the config switch acts when classes are transformed). -Dbons_and_furious.statusPingGroups=false also turns it off. */
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.statusPingGroups", "true"));

    private static final class Groups {
        Object owner;
        Map<String, List<Map.Entry<ResourceLocation, ?>>> byNamespace;
    }

    private static final ThreadLocal<Groups> CURRENT = ThreadLocal.withInitial(Groups::new);

    private StatusPingChannels() {
    }

    /** Start of toBuf: group this ping's channels by namespace. */
    public static void begin(Object ping, Map<ResourceLocation, ?> channels) {
        Groups g = CURRENT.get();
        if (!enabled) {
            g.owner = null;
            g.byNamespace = null;
            return;
        }
        Map<String, List<Map.Entry<ResourceLocation, ?>>> by = new HashMap<>();
        for (Map.Entry<ResourceLocation, ?> e : channels.entrySet()) {
            by.computeIfAbsent(e.getKey().m_135827_(), k -> new ArrayList<>()).add(e);
        }
        g.owner = ping;
        g.byNamespace = by;
    }

    /** In place of getChannelsForMod inside toBuf; null means "not grouped here, call the original". */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static List forMod(Object ping, String modId) {
        Groups g = CURRENT.get();
        if (g.owner != ping || g.byNamespace == null) return null;
        List<?> l = g.byNamespace.get(modId);
        return l != null ? l : List.of();
    }

    /** Every return of toBuf. */
    public static void end() {
        Groups g = CURRENT.get();
        g.owner = null;
        g.byNamespace = null;
    }
}
