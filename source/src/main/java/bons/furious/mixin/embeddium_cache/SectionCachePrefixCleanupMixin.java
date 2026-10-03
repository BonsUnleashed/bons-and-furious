package bons.furious.mixin.embeddium_cache;

import bons.furious.patch.embeddium_cache.SectionCacheCleanup;
import it.unimi.dsi.fastutil.longs.Long2ReferenceLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceCollection;
import java.util.function.Predicate;
import org.embeddedt.embeddium.impl.world.cloned.ClonedChunkSection;
import org.embeddedt.embeddium.impl.world.cloned.ClonedChunkSectionCache;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * embeddium_section_cache_prefix_cleanup (Embeddium 0.3.31+mc1.20.1, client only).
 *
 * ClonedChunkSectionCache.cleanup() runs every frame (RenderSectionManager.updateChunks): it sets time = System.nanoTime()
 * and then walks all of up to 512 cached sections with values().removeIf(expired). This redirect replaces only that walk.
 * While the cache's linked order is trusted to be sorted by last use (see SectionCacheCleanup), the expired sections are a
 * prefix of that order, and removing from the front while Embeddium's own predicate holds removes the same sections in the
 * same order and leaves the same linked order; the boolean result is discarded by cleanup() in both cases. The time
 * assignment before the walk is the original code. Whenever the order is not trusted (the cache's first cleanup, the
 * runtime switch off, a time smaller than the previous one, or the O(1) first-not-newer-than-last tripwire failing) the
 * original removeIf runs, followed by a check of the whole order that decides whether the next cleanup may use the prefix
 * walk. cleanup(), acquire() and invalidate() are all synchronized on the cache, so the redirect runs under the same lock
 * as every write to the map. The cache's time is written only by its constructor and cleanup() (class shape and every
 * method are fingerprinted), so every stamp an entry can carry is a time this redirect has already seen.
 */
@Mixin(value = ClonedChunkSectionCache.class, remap = false)
public abstract class SectionCachePrefixCleanupMixin {
    @Shadow
    @Final
    private Long2ReferenceLinkedOpenHashMap<ClonedChunkSection> positionToEntry;

    @Shadow
    private long time;

    /** The time of the previous cleanup. */
    @Unique
    private long bons$lastTime;

    /** True while every stamp is non-decreasing in linked order and none is newer than bons$lastTime. */
    @Unique
    private boolean bons$trusted;

    @Redirect(method = "cleanup", at = @At(value = "INVOKE",
            target = "Lit/unimi/dsi/fastutil/objects/ReferenceCollection;removeIf(Ljava/util/function/Predicate;)Z"))
    private boolean bons$removeExpired(ReferenceCollection<ClonedChunkSection> values, Predicate<? super ClonedChunkSection> expired) {
        long now = this.time;
        if (this.bons$trusted && now < this.bons$lastTime) {
            SectionCacheCleanup.clockWentBack(now, this.bons$lastTime);
            this.bons$trusted = false;
        }
        this.bons$lastTime = now;
        if (!SectionCacheCleanup.enabled) {
            this.bons$trusted = false;
            return values.removeIf(expired);
        }
        if (this.bons$trusted && SectionCacheCleanup.endsInOrder(this.positionToEntry)) {
            boolean removed = SectionCacheCleanup.removeExpiredPrefix(this.positionToEntry, expired);
            if (!SectionCacheCleanup.VERIFY) return removed;
            boolean extra = values.removeIf(expired);
            SectionCacheCleanup.verified(extra, this.positionToEntry.size());
            return removed | extra;
        }
        SectionCacheCleanup.full();
        boolean removed = values.removeIf(expired);
        this.bons$trusted = SectionCacheCleanup.ordered(this.positionToEntry, now);
        return removed;
    }
}
