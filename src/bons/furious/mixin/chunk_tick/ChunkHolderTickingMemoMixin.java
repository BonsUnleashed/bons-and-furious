package bons.furious.mixin.chunk_tick;

import bons.furious.patch.chunk_tick.TickingChunkMemo;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.datafixers.util.Either;
import java.util.concurrent.CompletableFuture;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

/**
 * vanilla_ticking_chunk_memo (Minecraft 1.20.1 on Forge 47.4.16, both sides; acts on the server): getTickingChunk
 * (m_140085_, vanilla's body or ModernFix 5.27.77's perf.ticking_chunk_alloc overwrite, which this wraps) answers from the
 * holder's own two fields while the holder's tickingChunkFuture (f_140003_) is the future they were recorded for, and
 * otherwise runs the method and records its answer when TickingChunkMemo.provable says so. Only the server thread of the
 * holder's level (f_142983_) reads or writes the record; it claims the holder once (bons$memoOwner), the first time it
 * records an answer. Three reference fields; no Minecraft code is carried.
 */
@Mixin(value = ChunkHolder.class, remap = false)
public abstract class ChunkHolderTickingMemoMixin {
    @Shadow
    private volatile CompletableFuture<Either<LevelChunk, ChunkHolder.ChunkLoadingFailure>> f_140003_;
    @Shadow
    @Final
    private LevelHeightAccessor f_142983_;
    /** The server thread of this holder's level once it has recorded an answer; written once, by that thread only. */
    @Unique
    private Thread bons$memoOwner;
    /** The future the answer below was recorded for (only bons$memoOwner reads or writes these two). */
    @Unique
    private CompletableFuture<?> bons$memoFuture;
    @Unique
    private LevelChunk bons$memoChunk;

    @WrapMethod(method = "m_140085_")
    private LevelChunk bons$tickingChunk(Operation<LevelChunk> original) {
        if (!TickingChunkMemo.enabled) return original.call();
        Thread self = Thread.currentThread();
        boolean owner = this.bons$memoOwner == self;
        CompletableFuture<?> before = this.f_140003_;
        if (owner && before != null && before == this.bons$memoFuture) {
            if (!TickingChunkMemo.SHADOW) return this.bons$memoChunk;
            LevelChunk answer = original.call();
            TickingChunkMemo.shadow(this.bons$memoChunk, answer);
            return answer;
        }
        LevelChunk answer = original.call();
        if (TickingChunkMemo.provable(before, this.f_140003_, answer)) {
            if (!owner) {
                if (this.bons$memoOwner != null || !TickingChunkMemo.isServerThread(this.f_142983_, self)) return answer;
                this.bons$memoOwner = self;
            }
            this.bons$memoChunk = answer;
            this.bons$memoFuture = before;
        }
        return answer;
    }
}
