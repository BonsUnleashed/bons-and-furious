package bons.furious.mixin.rpl_c49;

import bons.furious.patch.rpl_c49.RplIdleSkip;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import rbasamoyai.ritchiesprojectilelib.chunkloading.ChunkManager;

/**
 * rpl_idle_chunk_manager_skip (Ritchie's Projectile Library 2.1.2, MIT; server side): an @Inject at the HEAD of
 * ChunkManager.tick(ServerLevel). When chunks, queue, inQueue and loaded are all empty and a full tick already ran since
 * they last held anything (that tick found them empty, so it ended with them empty and trimmed), the tick returns at once;
 * otherwise the library's own tick runs. A tick that finds anything in them costs one emptiness test. Exact variant (see
 * RplIdleSkip): every statement of such an idle tick is a no-op except setDirty(), so the only difference is that an idle
 * manager is not marked for saving every tick.
 */
@Mixin(value = ChunkManager.class, remap = false)
public abstract class ChunkManagerIdleMixin {
    @Shadow @Final private LongOpenHashSet chunks;
    @Shadow @Final private LongArrayFIFOQueue queue;
    @Shadow @Final private LongOpenHashSet inQueue;
    @Shadow @Final private Long2IntOpenHashMap loaded;

    /** True once a full tick has started with all four collections empty and none has been added to since. */
    @Unique
    private boolean bons$rplC49Settled;

    @Inject(method = "tick(Lnet/minecraft/server/level/ServerLevel;)V", at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    private void bons$rplC49Head(ServerLevel level, CallbackInfo ci) {
        if (!RplIdleSkip.enabled || !this.loaded.isEmpty() || !this.queue.isEmpty() || !this.chunks.isEmpty() || !this.inQueue.isEmpty()) {
            this.bons$rplC49Settled = false;
            return;
        }
        if (this.bons$rplC49Settled) {
            RplIdleSkip.skipped();
            ci.cancel();
            return;
        }
        this.bons$rplC49Settled = true;
    }
}
