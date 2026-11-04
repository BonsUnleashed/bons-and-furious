package bons.furious.patch.vanilla_tick_lists;

/**
 * vanilla_level_ticks_blocked_park: implemented by ChunkMap (ChunkMapParkMixin): its visible chunk holder for a chunk
 * (ChunkMap.getVisibleChunkIfPresent, m_140327_, protected), which the tick check reads. No Mojang code.
 */
public interface TickParkChunkMap extends TickParkRouted {
    Object bons$visibleHolder(long chunk);
}
