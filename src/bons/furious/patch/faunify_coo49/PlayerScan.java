package bons.furious.patch.faunify_coo49;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch faunify_player_proximity_scan_lean (Faunify 1.1.3 and 1.3.3, both sides; the goals run on the
 * server).
 *
 * Several Faunify critters look for players near them with level.getEntitiesOfClass(Player.class, box): the mouse's
 * seed-stealing goal (10 blocks around it) in every Faunify build, and in 1.3.3 also the beefly's and the silk moth's
 * flight goals (4 blocks), the dragonfly's darting goal and its nearest-player search (4 and 6 blocks) and the silk moth's
 * own tick (10 blocks). Each such search walks every entity of every chunk section the box touches (two blocks wider)
 * to find the few that are players, every time the goal is asked (every other tick per critter).
 *
 * Trade-off (off by default): with the switch on these five searches are answered from the level's own player list:
 * the players that are not spectators (what the search keeps) and whose bounding box touches the box. What can differ:
 * the players come in the order they joined instead of the order of the chunk sections they stand in, so in multiplayer
 * a goal that takes the first player that fits (the mouse picks the first player carrying seeds) can pick another player
 * than before, and the dragonfly's nearest player can be another one when two stand exactly as far away; with
 * Valkyrien Skies installed the part of the box it adds in ship space is not searched; player entities that are not in
 * the level's player list (fake players some mods place in the world) are not seen, and a player resized to more than 4
 * blocks can be seen where the section search would have missed them. Nothing else changes. Runtime flag
 * -Dbons_and_furious.faunifyPlayerProximityScanLean=false. Faunify is MIT; no code of it is carried.
 */
public final class PlayerScan {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.faunifyPlayerProximityScanLean", "true"));
    private static volatile boolean announced;

    private PlayerScan() {
    }

    /** Level.getEntitiesOfClass(Player.class, box) from the level's player list; any other class runs the search. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static List players(Level level, Class<?> type, AABB box, Operation<List> original) {
        if (!enabled || type != Player.class) return original.call(level, type, box);
        List<? extends Player> all = level.m_6907_();
        List<Player> out = new ArrayList<>(Math.min(all.size(), 4));
        for (int i = 0, n = all.size(); i < n; i++) {
            Player p = all.get(i);
            if (!p.m_5833_() && p.m_20191_().m_82381_(box)) out.add(p);
        }
        if (!announced) announce();
        return out;
    }

    private static synchronized void announce() {
        if (announced) return;
        announced = true;
        LOGGER.info("Bons and Furious: faunify_player_proximity_scan_lean: Faunify's player searches read the level's player list");
    }
}
