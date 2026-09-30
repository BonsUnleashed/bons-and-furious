package bons.furious.mixin.valkyrienskies_mod;

import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.joml.Vector3ic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.valkyrienskies.core.internal.world.chunks.VsiTerrainUpdate;

/**
 * valkyrien_chunk_bookkeeping: access to the chunk bookkeeping members that VS's server.world.MixinServerLevel adds to
 * ServerLevel (the known-chunk map, the unload counters and the chunk-load routine). Accessors and invokers are
 * located after all mixins are merged, so they can reach members another mixin added.
 */
@Mixin(value = ServerLevel.class, remap = false)
public interface ServerLevelChunkBookkeepingAccess {
    @Accessor("vs$knownChunks")
    Map<ChunkPos, List<Vector3ic>> bons$knownChunks();

    @Accessor("vs$chunksToUnload")
    Long2LongOpenHashMap bons$chunksToUnload();

    @Invoker("vs$loadChunk")
    void bons$loadChunk(ChunkAccess worldChunk, List<VsiTerrainUpdate> voxelShapeUpdates);
}
