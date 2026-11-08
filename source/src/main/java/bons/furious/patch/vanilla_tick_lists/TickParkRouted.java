package bons.furious.patch.vanilla_tick_lists;

/**
 * vanilla_level_ticks_blocked_park: implemented by the classes whose changes can turn a level's tick check true
 * (ServerLevel, ChunkMap, TickingTracker, PersistentEntitySectionManager; one mixin each). They hand those changes to the
 * level's router. No Mojang code.
 */
public interface TickParkRouted {
    void bons$setTickParkRouter(TickPark.Router router);
}
