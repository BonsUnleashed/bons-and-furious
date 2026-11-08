package bons.furious.patch.netherexp_coo49;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.neoforged.fml.ModList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch netherexp_candle_player_scan_lean (Jaden's Nether Expansion; 1.21.1 tested build: Jaden's Nether
 * Expansion 2.4.1; server).
 *
 * A lit Cierge of Treachery (2.3.5's treacherous candle) searches, on every tick, every entity section within 16 blocks for
 * server players (CiergeOfTreacheryBlockEntity.getPlayersInRadius: Level.getEntitiesOfClass(ServerPlayer, box), spectators
 * excluded) and hands the list to updateBossBarPlayers, which gives each listed player its Betrayed effect and the boss bar
 * and takes both from boss bar players not listed. That consumer treats each player on its own (per player: an effect, a
 * boss bar membership; the second loop only asks "is this player listed"), so the order of the list does not matter.
 *
 * The switch answers the same question from the level's player list: every non-spectator player whose box intersects
 * the search box and whose section lies in the section range vanilla's search walks (EntitySectionStorage: x and z 2
 * blocks wider, y 4 blocks lower; the same in 1.21.1: so even an unusually large player is listed exactly when vanilla's
 * search would list it). The same players, in the player list's order instead of section order. Exact for every block, with
 * one stand-down: Valkyrien Skies changes entity box searches near its ships (a block on a ship sits in shipyard
 * coordinates); with it installed the original search always runs. Runtime flag
 * -Dbons_and_furious.netherexpCandlePlayerScanLean=false. Nether Expansion is CC BY-NC-ND 4.0: no code of it is carried.
 */
public final class CandlePlayerScan {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.netherexpCandlePlayerScanLean", "true"));
    /** False with Valkyrien Skies (it rewrites entity box searches near ships), or when the mod list cannot be read. */
    public static final boolean ACTIVE = !standDown();
    private static volatile boolean announced;

    static {
        if (!ACTIVE)
            LOGGER.info("Bons and Furious: netherexp_candle_player_scan_lean stands down: Valkyrien Skies is installed (it changes entity box searches near ships); the Cierge of Treachery keeps its own search");
    }

    private CandlePlayerScan() {
    }

    private static boolean standDown() {
        try {
            ModList mods = ModList.get();
            return mods == null || mods.isLoaded("valkyrienskies");
        } catch (Throwable t) {
            return true;
        }
    }

    /** The block entity's player search: from the player list (switch on, no Valkyrien Skies), else the original. */
    public static List<?> players(Level level, Class<?> type, AABB box, Operation<List<?>> original) {
        if (!ACTIVE || !enabled || type != ServerPlayer.class || !(level instanceof ServerLevel server)) return original.call(level, type, box);
        int x0 = SectionPos.blockToSectionCoord(box.minX - 2.0), x1 = SectionPos.blockToSectionCoord(box.maxX + 2.0);
        int y0 = SectionPos.blockToSectionCoord(box.minY - 4.0), y1 = SectionPos.blockToSectionCoord(box.maxY);
        int z0 = SectionPos.blockToSectionCoord(box.minZ - 2.0), z1 = SectionPos.blockToSectionCoord(box.maxZ + 2.0);
        List<ServerPlayer> all = server.players();
        ArrayList<ServerPlayer> out = new ArrayList<>(Math.min(all.size(), 8));
        for (int i = 0, n = all.size(); i < n; i++) {
            ServerPlayer p = all.get(i);
            if (p.isSpectator()) continue;
            BlockPos at = p.blockPosition();
            int sx = at.getX() >> 4, sy = at.getY() >> 4, sz = at.getZ() >> 4;
            if (sx < x0 || sx > x1 || sy < y0 || sy > y1 || sz < z0 || sz > z1) continue;
            if (!p.getBoundingBox().intersects(box)) continue;
            out.add(p);
        }
        if (!announced) announce();
        return out;
    }

    private static synchronized void announce() {
        if (announced) return;
        announced = true;
        LOGGER.info("Bons and Furious: netherexp_candle_player_scan_lean: the Cierge of Treachery finds its players in the player list");
    }
}
