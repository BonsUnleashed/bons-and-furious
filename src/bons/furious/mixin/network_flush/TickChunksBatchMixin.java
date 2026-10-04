package bons.furious.mixin.network_flush;

import bons.furious.patch.network_flush.FlushBatch;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.server.level.ServerChunkCache;
import org.spongepowered.asm.mixin.Mixin;

/**
 * vanilla_connection_flush_batching (Minecraft 1.20.1 on Forge 47.4.16, both sides; acts on the server thread):
 * ServerChunkCache.tickChunks (m_8490_), which broadcasts every chunk's block and light changes and ends with the entity
 * tracker (ChunkMap.tick), runs inside a FlushBatch for all connections; at its end each connection that got batched
 * packets is flushed once. The method itself (with every other mod's hooks inside it) runs unchanged.
 */
@Mixin(value = ServerChunkCache.class, remap = false)
public abstract class TickChunksBatchMixin {
    @WrapMethod(method = "m_8490_")
    private void bons$batchBursts(Operation<Void> original) {
        boolean mine = FlushBatch.begin(null);
        try {
            original.call();
        } finally {
            if (mine) FlushBatch.end();
        }
    }
}
