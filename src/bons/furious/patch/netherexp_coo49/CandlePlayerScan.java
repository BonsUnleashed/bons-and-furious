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
import net.minecraftforge.fml.ModList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch netherexp_candle_player_scan_lean (Jaden's Nether Expansion 2.3.5, server).
 *
 * A lit treacherous candle searches, on every tick, every entity section within 16 blocks for server players
 * (TreacherousCandleBlockEntity.getPlayersInRadius: Level.getEntitiesOfClass(ServerPlayer, box), spectators excluded)
 * and hands the list to updateBossBarPlayers, which gives each listed player its Betrayed effect and the boss bar and
 * takes both from boss bar players not listed. That consumer treats each player on its own (per player: an effect, a
 * boss bar membership; the second loop only asks "is this player listed"), so the order of the list does not matter.
 *
 * The switch answers the same question from the level's player list: every non-spectator player whose box intersects
 * the search box and whose section lies in the section range vanilla's search walks (x and z 2 blocks wider, y 4 blocks
 * lower: so even an unusually large player is listed exactly when vanilla's search would list it). The same players, in
 * the player list's order instead of section order. Exact for every candle, with one stand-down: Valkyrien Skies changes
 * entity box searches near its ships (a candle on a ship sits in shipyard coordinates); with it installed (this pack)
 * the original search always runs. Runtime flag -Dbons_and_furious.netherexpCandlePlayerScanLean=false.
 * Nether Expansion is CC-BY-NC-SA-4.0: no code of it is carried.
 */
public final class CandlePlayerScan {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.netherexpCandlePlayerScanLean", "true"));
    /** False with Valkyrien Skies (it rewrites entity box searches near ships), or when the mod list cannot be read. */
    public static final boolean ACTIVE = !standDown();
    private static volatile boolean announced;

    static {
        if (!ACTIVE)
            LOGGER.info("Bons and Furious: netherexp_candle_player_scan_lean stands down: Valkyrien Skies is installed (it changes entity box searches near ships); treacherous candles keep their own search");
    }

    private CandlePlayerScan() {
    }

    private static boolean standDown() {
        try {
            ModList mods = ModList.get();
            return mods != null && mods.isLoaded("valkyrienskies");
        } catch (Throwable t) {
            return true;
        }
    }

    /** The candle's player search: from the player list (switch on, no Valkyrien Skies), else the original. */
    public static List<?> players(Level level, Class<?> type, AABB box, Operation<List<?>> original) {
        if (!ACTIVE || !enabled || type != ServerPlayer.class || !(level instanceof ServerLevel server)) return original.call(level, type, box);
        int x0 = SectionPos.m_235865_(box.f_82288_ - 2.0), x1 = SectionPos.m_235865_(box.f_82291_ + 2.0);
        int y0 = SectionPos.m_235865_(box.f_82289_ - 4.0), y1 = SectionPos.m_235865_(box.f_82292_);
        int z0 = SectionPos.m_235865_(box.f_82290_ - 2.0), z1 = SectionPos.m_235865_(box.f_82293_ + 2.0);
        List<ServerPlayer> all = server.m_6907_();
        ArrayList<ServerPlayer> out = new ArrayList<>(Math.min(all.size(), 8));
        for (int i = 0, n = all.size(); i < n; i++) {
            ServerPlayer p = all.get(i);
            if (p.m_5833_()) continue;
            BlockPos at = p.m_20183_();
            int sx = at.m_123341_() >> 4, sy = at.m_123342_() >> 4, sz = at.m_123343_() >> 4;
            if (sx < x0 || sx > x1 || sy < y0 || sy > y1 || sz < z0 || sz > z1) continue;
            if (!p.m_20191_().m_82381_(box)) continue;
            out.add(p);
        }
        if (!announced) announce();
        return out;
    }

    private static synchronized void announce() {
        if (announced) return;
        announced = true;
        LOGGER.info("Bons and Furious: netherexp_candle_player_scan_lean: treacherous candles find their players in the player list");
    }
}
