package bons.furious.patch.vanilla_tick_lists;

import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TickingTracker;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import net.minecraft.world.ticks.LevelTicks;

/**
 * vanilla_level_ticks_blocked_park: wires one ServerLevel at the end of its constructor (ServerLevelTickParkMixin). The
 * level parks only when every part its tick check reads is exactly the vanilla class (no subclass that could answer from
 * other inputs): the level, its chunk source, chunk map, distance manager (ChunkMap$DistanceManager), ticking tracker,
 * entity manager and both LevelTicks; and only while C2ME is not installed (C2ME_MODULES; presence decides, no C2ME class is
 * read). Otherwise the level keeps the stock walk (one INFO line). Mojang member names; no Mojang code.
 */
public final class TickParkAttach {
    private static final String VANILLA_DISTANCE_MANAGER = "net.minecraft.server.level.ChunkMap$DistanceManager";
    /**
     * C2ME mod ids whose presence keeps every level on the stock walk: mod id, why. The modules' own ids first, the
     * all-in-one jar's id last (0.4.0-alpha.0.122 bundles both modules; an untested C2ME build is treated the same way).
     */
    private static final String[][] C2ME_MODULES = {
            {"c2me_rewrites_chunk_system",
                    "C2ME's chunk system rewrite replaces ChunkMap.getVisibleChunkIfPresent and the chunk holders, so a chunk's visible holder no longer changes only in promoteChunkMap"},
            {"c2me_notickvd",
                    "C2ME's no-tick view distance changes the ticking tracker's levels (constants in its constructor and setLevel)"},
            {"c2me",
                    "C2ME is installed, and its chunk system rewrite (bundled in 0.4.0-alpha.0.122) replaces ChunkMap.getVisibleChunkIfPresent and the chunk holders"}};
    /** Why C2ME keeps the stock walk, or null when no C2ME module is installed; decided once from the mod list. */
    private static final String C2ME_WHY = c2meWhy();

    private TickParkAttach() {
    }

    private static String c2meWhy() {
        try {
            var mods = net.neoforged.fml.loading.LoadingModList.get();
            if (mods == null) return null;
            for (String[] m : C2ME_MODULES) {
                if (mods.getModFileById(m[0]) != null) return m[1] + " (" + m[0] + " is installed)";
            }
            return null;
        } catch (Throwable t) {
            return "could not check whether C2ME is installed (" + t + ")";
        }
    }

    public static void attach(ServerLevel level, LevelTicks<?> blockTicks, LevelTicks<?> fluidTicks, ServerChunkCache chunkSource,
                              PersistentEntitySectionManager<?> entityManager) {
        String why = null;
        ChunkMap chunkMap = chunkSource == null ? null : chunkSource.chunkMap;
        DistanceManager distance = chunkMap == null ? null : chunkMap.getDistanceManager();
        Object tracker = distance instanceof TickParkDistanceManager d ? d.bons$tickingTracker() : null;
        if (C2ME_WHY != null) why = C2ME_WHY;
        else if (level.getClass() != ServerLevel.class) why = "the level is a " + level.getClass().getName();
        else if (chunkSource == null || chunkSource.getClass() != ServerChunkCache.class) why = "its chunk source is not the vanilla one";
        else if (chunkMap == null || chunkMap.getClass() != ChunkMap.class) why = "its chunk map is not the vanilla one";
        else if (distance == null || !VANILLA_DISTANCE_MANAGER.equals(distance.getClass().getName())) why = "its distance manager is not the vanilla one";
        else if (tracker == null || tracker.getClass() != TickingTracker.class)
            why = "its ticking tracker is " + (tracker == null ? "missing" : tracker.getClass().getName());
        else if (entityManager == null || entityManager.getClass() != PersistentEntitySectionManager.class) why = "its entity manager is not the vanilla one";
        else if (blockTicks == null || blockTicks.getClass() != LevelTicks.class || fluidTicks == null || fluidTicks.getClass() != LevelTicks.class)
            why = "its tick schedulers are not the vanilla ones";
        else if (!(blockTicks instanceof TickParkHost) || !(fluidTicks instanceof TickParkHost) || !(chunkMap instanceof TickParkChunkMap)
                || !(tracker instanceof TickParkRouted) || !(entityManager instanceof TickParkRouted) || !(((Object) level) instanceof TickParkRouted))
            why = "not every part carries the switch's hooks";
        if (why != null) {
            TickPark.declined(String.valueOf(level.dimension().location()), why);
            return;
        }
        TickParkChunkMap holders = (TickParkChunkMap) chunkMap;
        TickPark.Router router = new TickPark.Router(Thread.currentThread(), holders::bons$visibleHolder);
        ((TickParkHost) blockTicks).bons$setTickPark(router.block);
        ((TickParkHost) fluidTicks).bons$setTickPark(router.fluid);
        holders.bons$setTickParkRouter(router);
        ((TickParkRouted) tracker).bons$setTickParkRouter(router);
        ((TickParkRouted) entityManager).bons$setTickParkRouter(router);
        ((TickParkRouted) (Object) level).bons$setTickParkRouter(router);
    }
}
