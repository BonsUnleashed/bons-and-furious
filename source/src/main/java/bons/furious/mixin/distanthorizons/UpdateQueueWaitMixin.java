package bons.furious.mixin.distanthorizons;

import bons.furious.patch.distanthorizons.UpdateQueueWait;
import com.google.common.cache.Cache;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.seibel.distanthorizons.core.api.internal.chunkUpdating.ChunkPosQueue;
import com.seibel.distanthorizons.core.api.internal.chunkUpdating.ChunkUpdateData;
import com.seibel.distanthorizons.core.api.internal.chunkUpdating.ChunkUpdateQueueManager;
import com.seibel.distanthorizons.core.logging.DhLogger;
import com.seibel.distanthorizons.core.pos.DhChunkPos;
import com.seibel.distanthorizons.core.wrapperInterfaces.chunk.IChunkWrapper;
import java.util.concurrent.AbstractExecutorService;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * distanthorizons_update_queue_wait (Distant Horizons 3.3.2, LGPL-3.0; both sides).
 *
 * runQueueingLoop is DH's own loop with one change: when the queues are not empty and the round made no progress, the
 * thread parks briefly instead of starting the next round at once (see {@link UpdateQueueWait}). Progress = a
 * pre-update was taken off its queue (processQueuedChunkPreUpdate) or an update was handed to the LOD builder executor
 * (tryDispatchReadyChunkUpdate). Everything else, including the 50 ms sleep and cache clean-up when both queues are
 * empty and the error handling, is unchanged. No other mod mixes into this class.
 */
@Mixin(value = ChunkUpdateQueueManager.class, remap = false)
public abstract class UpdateQueueWaitMixin {
    @Shadow
    @Final
    private static DhLogger LOGGER;
    @Shadow
    @Final
    public ChunkPosQueue updateQueue;
    @Shadow
    @Final
    public ChunkPosQueue preUpdateQueue;
    @Shadow
    @Final
    public Cache<DhChunkPos, IChunkWrapper> queuedChunkWrapperByChunkPos;

    @Unique
    private volatile boolean bons$progress;

    @Shadow
    public abstract void processQueue();

    /**
     * @author BonsUnleashed
     * @reason Park after an idle round instead of spinning while the closest update is inside DH's 250 ms debounce.
     */
    @Overwrite
    private void runQueueingLoop() {
        while (!Thread.interrupted()) {
            try {
                this.bons$progress = false;
                this.processQueue();
                if (!this.updateQueue.isEmpty() || !this.preUpdateQueue.isEmpty()) {
                    if (!this.bons$progress && UpdateQueueWait.enabled) {
                        UpdateQueueWait.idle();
                    }
                    continue;
                }
                Thread.sleep(50L);
                this.queuedChunkWrapperByChunkPos.cleanUp();
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                LOGGER.error("Unexpected error in chunk update queueing thread, error: [" + e.getMessage() + "].", new Object[]{e});
            }
        }
    }

    @WrapOperation(method = "processQueuedChunkPreUpdate", at = @At(value = "INVOKE",
            target = "Lcom/seibel/distanthorizons/core/api/internal/chunkUpdating/ChunkPosQueue;popClosest()Lcom/seibel/distanthorizons/core/api/internal/chunkUpdating/ChunkUpdateData;"))
    private ChunkUpdateData bons$preUpdateTaken(ChunkPosQueue queue, Operation<ChunkUpdateData> original) {
        ChunkUpdateData data = original.call(queue);
        if (data != null) {
            this.bons$progress = true;
        }
        return data;
    }

    // javac emitted the call against the declaring class: AbstractExecutorService.execute (the executor is DH's
    // PriorityTaskPicker$Executor)
    @WrapOperation(method = "tryDispatchReadyChunkUpdate", at = @At(value = "INVOKE",
            target = "Ljava/util/concurrent/AbstractExecutorService;execute(Ljava/lang/Runnable;)V"))
    private void bons$updateDispatched(AbstractExecutorService executor, Runnable task, Operation<Void> original) {
        original.call(executor, task);
        this.bons$progress = true;      // reached only when execute did not throw (a rejection is re-queued by DH)
    }
}
