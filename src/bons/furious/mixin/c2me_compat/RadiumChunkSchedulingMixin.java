package bons.furious.mixin.c2me_compat;

import com.mojang.datafixers.util.Either;
import java.util.concurrent.CompletableFuture;
import me.jellysquid.mods.lithium.common.world.chunk.ChunkHolderExtended;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Switch radium_c2me_chunk_access (Radium Re-Reforged 0.14.3 with C2ME 0.2.0+alpha.12): applied only together with the
 * Radium option that switch gives back, i.e. only while C2ME is installed.
 *
 * When a chunk status is missing, Radium's getChunkBlocking (merged into ServerChunkCache by Radium's chunk_access
 * mixin) schedules it itself: ChunkMap.schedule, then updateChunkToSave and its own write of the holder's future slot.
 * Vanilla asks the holder instead (ChunkHolder.getOrScheduleFuture), and C2ME's threading fixes and threaded worldgen
 * rewrite exactly that method: they take a per-holder lock and schedule a status only after its parent status. These
 * redirects send Radium's slow path through getOrScheduleFuture, so scheduling is the same as without Radium; the
 * fast path (a ready chunk answered from the holder without a new ticket on every call) is unchanged.
 */
@Mixin(value = ServerChunkCache.class, priority = 1500, remap = false)
public abstract class RadiumChunkSchedulingMixin {
    @Redirect(method = "getChunkBlocking", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ChunkMap;m_140292_(Lnet/minecraft/server/level/ChunkHolder;Lnet/minecraft/world/level/chunk/ChunkStatus;)Ljava/util/concurrent/CompletableFuture;"))
    private CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>> bons$scheduleThroughHolder(ChunkMap chunkMap, ChunkHolder holder, ChunkStatus status) {
        return holder.m_140049_(status, chunkMap);
    }

    /** getOrScheduleFuture has already registered the save dependency of the future it returned. */
    @Redirect(method = "getChunkBlocking", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ChunkHolder;m_143017_(Ljava/util/concurrent/CompletableFuture;Ljava/lang/String;)V"))
    private void bons$saveDependencyAlreadyAdded(ChunkHolder holder, CompletableFuture<?> future, String reason) {
    }

    /** getOrScheduleFuture has already stored the future in the holder's slot for that status. */
    @Redirect(method = "getChunkBlocking", at = @At(value = "INVOKE",
            target = "Lme/jellysquid/mods/lithium/common/world/chunk/ChunkHolderExtended;setFutureForStatus(ILjava/util/concurrent/CompletableFuture;)V"))
    private void bons$futureAlreadyStored(ChunkHolderExtended holder, int index, CompletableFuture<?> future) {
    }
}
