package bons.furious.mixin.naturesaura_watch;

import bons.furious.patch.naturesaura_watch.WatchedChunkPurge;
import com.google.common.collect.SetMultimap;
import de.ellpeck.naturesaura.events.CommonEvents;
import java.util.UUID;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * naturesaura_watched_chunk_purge (Nature's Aura 39.4, MIT; server). Fix, exact variant. The static initializer hands
 * PENDING_AURA_CHUNKS to {@link WatchedChunkPurge}, which registers a ChunkWatchEvent.UnWatch listener. No Nature's Aura code
 * is carried.
 */
@Mixin(value = CommonEvents.class, remap = false)
public abstract class CommonEventsWatchMixin {
    @Shadow
    @Final
    private static SetMultimap<UUID, ChunkPos> PENDING_AURA_CHUNKS;

    @Inject(method = "<clinit>", at = @At("RETURN"))
    private static void bons$trackPending(CallbackInfo ci) {
        WatchedChunkPurge.track(PENDING_AURA_CHUNKS);
    }
}
