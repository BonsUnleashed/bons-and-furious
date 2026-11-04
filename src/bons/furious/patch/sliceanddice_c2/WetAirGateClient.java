package bons.furious.patch.sliceanddice_c2;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * Bons and Furious switch sliceanddice_wet_air_gate, the client's chunk lookup (since 1.0.49; a class of its own so that no
 * client class is ever resolved on a dedicated server: WetAirGate calls it for a client-side level only).
 *
 * The column's chunk of a ClientLevel, or null when the original must run: not on the client's main thread (a mod may ask
 * isRainingAt from another thread; client palettes are written on the main thread, which keeps WetAirGate's kept palette
 * scans exact there), or no chunk at that position. ClientChunkCache.getChunk(x, z, FULL, load = false) is the lookup the
 * original's block reads make (with load = true they get the cache's empty chunk instead of null when the position is not
 * loaded); the client has no chunk tickets, so the gate's lookup leaves nothing behind.
 */
public final class WetAirGateClient {
    private WetAirGateClient() {
    }

    static LevelChunk loadedChunk(Level level, BlockPos pos) {
        if (!(level instanceof ClientLevel clientLevel)) return null;
        Minecraft mc = Minecraft.m_91087_();
        if (mc == null || !mc.m_18695_()) return null;
        return clientLevel.m_7726_().m_7587_(pos.m_123341_() >> 4, pos.m_123343_() >> 4, ChunkStatus.f_62326_, false);
    }
}
