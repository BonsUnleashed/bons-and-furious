package bons.furious.patch.spawn_gate;

/**
 * vanilla_spawn_gate_visibility_memo: implemented by LevelChunk through bons.furious.mixin.spawn_gate.LevelChunkVisibilityMixin.
 * The chunk's last spawning-gate answer, the epoch group of its position and the group's epoch when it was asked
 * (stamp 0 = never asked; epochs start at 1, so 0 never matches). Read and written by the server thread only
 * (ServerChunkCache.tickChunks).
 *
 * Ported to 1.21.1: unchanged (Minecraft 1.21.1 with NeoForge 21.1.252).
 */
public interface ChunkVisibilityMemo {
    long bons$visStamp();

    int bons$visGroup();

    boolean bons$visTicking();

    void bons$visRemember(long stamp, int group, boolean ticking);
}
