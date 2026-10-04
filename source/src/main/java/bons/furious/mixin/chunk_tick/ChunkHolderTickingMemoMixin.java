package bons.furious.mixin.chunk_tick;

import bons.furious.patch.chunk_tick.TickingChunkMemo;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.concurrent.CompletableFuture;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkResult;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

/**
 * vanilla_ticking_chunk_memo (Minecraft 1.21.1 with NeoForge 21.1.252, both sides; acts on the server): getTickingChunk
 * (vanilla's body; no pinned 1.21.1 mod mixes into it) answers from the holder's own two fields while the holder's
 * tickingChunkFuture is the future they were recorded for, and otherwise runs the method and records its answer when
 * TickingChunkMemo.provable says so. Only the server thread of the holder's level (levelHeightAccessor) reads or writes
 * the record; it claims the holder once (bons$memoOwner), the first time it records an answer. A holder whose class is
 * not exactly ChunkHolder (C2ME's chunk system rewrite uses a subclass with its own getTickingChunkFuture) runs the
 * method on every call. Three reference fields; no Minecraft code is carried.
 *
 * Ported to 1.21.1: the shadowed future is CompletableFuture&lt;ChunkResult&lt;LevelChunk&gt;&gt; (was Either); the exact-class
 * test is new; the rest is unchanged.
 */
@Mixin(value = ChunkHolder.class, remap = false)
public abstract class ChunkHolderTickingMemoMixin {
    @Shadow
    private volatile CompletableFuture<ChunkResult<LevelChunk>> tickingChunkFuture;
    @Shadow
    @Final
    private LevelHeightAccessor levelHeightAccessor;
    /** The server thread of this holder's level once it has recorded an answer; written once, by that thread only. */
    @Unique
    private Thread bons$memoOwner;
    /** The future the answer below was recorded for (only bons$memoOwner reads or writes these two). */
    @Unique
    private CompletableFuture<?> bons$memoFuture;
    @Unique
    private LevelChunk bons$memoChunk;

    @WrapMethod(method = "getTickingChunk")
    private LevelChunk bons$tickingChunk(Operation<LevelChunk> original) {
        if (!TickingChunkMemo.enabled || ((Object) this).getClass() != ChunkHolder.class) return original.call();
        Thread self = Thread.currentThread();
        boolean owner = this.bons$memoOwner == self;
        CompletableFuture<?> before = this.tickingChunkFuture;
        if (owner && before != null && before == this.bons$memoFuture) {
            if (!TickingChunkMemo.SHADOW) return this.bons$memoChunk;
            LevelChunk answer = original.call();
            TickingChunkMemo.shadow(this.bons$memoChunk, answer);
            return answer;
        }
        LevelChunk answer = original.call();
        if (TickingChunkMemo.provable(before, this.tickingChunkFuture, answer)) {
            if (!owner) {
                if (this.bons$memoOwner != null || !TickingChunkMemo.isServerThread(this.levelHeightAccessor, self)) return answer;
                this.bons$memoOwner = self;
            }
            this.bons$memoChunk = answer;
            this.bons$memoFuture = before;
        }
        return answer;
    }
}
