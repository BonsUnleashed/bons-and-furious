package bons.furious.patch.cognition_coo49;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch cognition_player_scan_lean (Cognition, MIT; 1.21.1 tested build: Cognition 2.4.13 for NeoForge
 * 1.21.1, both sides; kind change, off by default; helper of FountainPlayerScanMixin and ObeliskPlayerScanMixin; no
 * Cognition code carried).
 *
 * What Cognition does. Every bound experience fountain asks, every tick on both sides, level.getEntitiesOfClass(Player.class,
 * the small box above it) and only looks at whether the list is empty (player above: sound + block update); the experience
 * obelisk asks once a second on the server for every player in its radius and serves the FIRST listed player who is alive,
 * has a memory tablet linked to it and experience to recover. The search (Level.getEntities with EntitySelector.NO_SPECTATORS)
 * walks every entity section the box touches, then NeoForge's part entities.
 *
 * What the switch does. Those two searches are answered from the level's player list instead: every player of
 * level.players() (in that list's order) that is not a spectator and whose bounding box intersects the box - the same test the
 * search makes for each entity (NO_SPECTATORS, AABB.intersects; a part entity is never a Player). The profiler's
 * "getEntities" count is made as the search makes it. Trade-off (off by default): with two or more matching players the
 * obelisk serves them in the level's player-list order (who joined first) instead of the entity-section order, so a
 * different linked player may get his experience first; a player whose chunk section is not (yet) accessible (just
 * teleported in) can be seen a tick earlier. The switch stands aside (the original search runs) while Valkyrien Skies is
 * installed: a fountain or obelisk on a ship sits in shipyard coordinates, where only the search's own box hooks find the
 * players standing on the ship. Nothing is saved or sent differently. Runtime flag bons_and_furious.cognitionPlayerScanLean.
 */
public final class PlayerScan {
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.cognitionPlayerScanLean", "true"));
    public static final String KEY = "cognition_player_scan_lean";
    /** Statistics for probes (plain fields): searches answered from the player list. */
    public static long answered;
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    private static volatile boolean announced;

    private PlayerScan() {
    }

    /** Valkyrien Skies is installed (checked once; offline, without FML's list: false). */
    private static final class Ships {
        static final boolean PRESENT = present();

        private static boolean present() {
            try {
                net.neoforged.fml.loading.LoadingModList list = net.neoforged.fml.loading.LoadingModList.get();
                boolean vs = list != null && list.getModFileById("valkyrienskies") != null;
                if (vs) LOGGER.info("Bons and Furious: {} stands aside: Valkyrien Skies is installed (fountains and obelisks on ships need its box hooks)", KEY);
                return vs;
            } catch (Throwable t) {
                return true;   // cannot tell: keep the original search
            }
        }
    }

    /** The fountain's / obelisk's getEntitiesOfClass(Player.class, box), wrapped. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static List players(Level level, Class<?> searched, AABB box, Operation<List> original) {
        if (!enabled || searched != Player.class || level == null || Ships.PRESENT) return original.call(level, searched, box);
        level.getProfiler().incrementCounter("getEntities");
        List out = new ArrayList();
        for (Player p : level.players()) {
            if (!p.isSpectator() && p.getBoundingBox().intersects(box)) out.add(p);
        }
        answered++;
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: {}: Cognition fountains and obelisks look for players in the level's player list", KEY);
        }
        return out;
    }
}
