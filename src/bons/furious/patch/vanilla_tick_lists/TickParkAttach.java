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
 * entity manager and both LevelTicks; C2ME's no-tick view distance, for instance, swaps the ticking tracker. Otherwise the
 * level keeps the stock walk (one INFO line). SRG member names; no Mojang code.
 */
public final class TickParkAttach {
    private static final String VANILLA_DISTANCE_MANAGER = "net.minecraft.server.level.ChunkMap$DistanceManager";

    private TickParkAttach() {
    }

    public static void attach(ServerLevel level, LevelTicks<?> blockTicks, LevelTicks<?> fluidTicks, ServerChunkCache chunkSource,
                              PersistentEntitySectionManager<?> entityManager) {
        String why = null;
        ChunkMap chunkMap = chunkSource == null ? null : chunkSource.f_8325_;
        DistanceManager distance = chunkMap == null ? null : chunkMap.m_143145_();
        Object tracker = distance instanceof TickParkDistanceManager d ? d.bons$tickingTracker() : null;
        if (level.getClass() != ServerLevel.class) why = "the level is a " + level.getClass().getName();
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
            TickPark.declined(String.valueOf(level.m_46472_().m_135782_()), why);
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
