package bons.furious.patch.timm_c49;

import com.github.charlyb01.timm.music.StructurePlaylist;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.logging.log4j.LogManager;

/**
 * Bons and Furious switch timm_structure_scan_idle_skip (The Immersive Music Mod 1.1.0, tested build; server side incl. the
 * integrated server; since 1.0.49). Kind: exact variant. Idea: Collections Of Optimizations (timm.skipStructureScan), idea
 * text only.
 *
 * What TIMM does. Its ServerPlayerMixin.onTick handler (merged at the HEAD of ServerPlayer.doTick) runs once a second per
 * player (tick count % 20 == the player's slot, creative players skipped): it asks the level's StructureManager for every
 * structure referenced by the 25 chunks within two chunks of the player (getAllStructuresAt: a chunk lookup each, which
 * loads a chunk that is not loaded yet), and for every structure whose biomes come from a tag it looks the tag's last name
 * up in StructurePlaylist.DISTANCE_FROM_STRUCTURE. With no entry it logs "Structure distance was not registered for:
 * <name>" and goes on; only an entry can lead to the structure test and the play packet. TIMM 1.1.0 never fills that map
 * (StructurePlaylist.init has no caller), so every scan ends that way.
 *
 * What the switch does. MixinSquared @TargetHandler + cancellable @Inject in that handler, right before its structure
 * lookups (the ServerLevel.structureManager() call, i.e. after TIMM's own creative and once-a-second tests): while
 * DISTANCE_FROM_STRUCTURE is empty the handler returns there. With an empty map the stock loop cannot send a packet, change
 * a field or start a song (every structure takes the "not registered" branch), so the result is the same; what is left out
 * is the lookups themselves: their log lines and, for a player whose nearby chunks are not loaded yet, the early load of
 * those chunks. As soon as anything puts an entry into the map (a later TIMM build that loads its playlist, another mod),
 * the handler runs as shipped again.
 *
 * -Dbons_and_furious.timmStructureScanIdleSkip=false runs the handler as shipped (emergency off). SKIPPED counts the
 * scans left out.
 */
public final class TimmStructureIdle {
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.timmStructureScanIdleSkip", "true"));
    public static final AtomicLong SKIPPED = new AtomicLong();
    private static volatile boolean announced;

    private TimmStructureIdle() {
    }

    /** True = return from TIMM's handler before its structure lookups (the playlist map has no entry). */
    public static boolean skip() {
        if (!enabled) return false;
        Map<String, Integer> distances = StructurePlaylist.DISTANCE_FROM_STRUCTURE;
        if (distances == null || !distances.isEmpty()) return false;
        SKIPPED.incrementAndGet();
        if (!announced) announce();
        return true;
    }

    private static synchronized void announce() {
        if (announced) return;
        announced = true;
        LogManager.getLogger("Bons and Furious").info("Bons and Furious: timm_structure_scan_idle_skip applies (The Immersive Music Mod has no structure playlist, so its once-a-second structure lookups around each player are left out)");
    }
}
