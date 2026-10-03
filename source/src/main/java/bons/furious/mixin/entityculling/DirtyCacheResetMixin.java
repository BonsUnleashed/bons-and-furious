package bons.furious.mixin.entityculling;

import com.logisticscraft.occlusionculling.cache.ArrayOcclusionCache;
import java.util.Arrays;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

/**
 * entityculling_dirty_cache_reset (EntityCulling 1.10.5, OcclusionCulling library, client).
 *
 * The culling thread resets its occlusion cache (two bits per voxel; 4 MiB at tracingDistance 128) with Arrays.fill before
 * every pass, about 96 times a second, while a pass only writes the voxels its rays visited. The four setters, the only
 * writers of the array, now note the index of every byte they turn from zero to non-zero, and the reset zeroes exactly
 * those bytes; past 8,192 noted bytes in one pass (more than clearing them individually is worth) it falls back to the
 * full fill. After every reset the array is all zeros, as before, so every later read returns what it did before.
 * The cache is MIT-licensed OcclusionCulling code; the method bodies are carried with the noting added.
 */
@Mixin(value = ArrayOcclusionCache.class, remap = false)
public abstract class DirtyCacheResetMixin {
    @Shadow
    @Final
    private int reachX2;
    @Shadow
    @Final
    private byte[] cache;
    @Shadow
    private int positionKey;
    @Shadow
    private int entry;
    @Shadow
    private int offset;

    @Unique
    private static final int BONS$MAX_DIRTY = 8192;
    @Unique
    private final int[] bons$dirty = new int[BONS$MAX_DIRTY];
    @Unique
    private int bons$dirtyCount;
    @Unique
    private boolean bons$overflow;

    /** Note a byte that is about to become non-zero (once per reset; nothing to note after an overflow). */
    @Unique
    private void bons$mark(int n) {
        if (this.cache[n] != 0 || this.bons$overflow) return;
        if (this.bons$dirtyCount == BONS$MAX_DIRTY) {
            this.bons$overflow = true;
            return;
        }
        this.bons$dirty[this.bons$dirtyCount++] = n;
    }

    /**
     * @author Bons and Furious (entityculling_dirty_cache_reset)
     * @reason zero only the bytes written since the last reset (the full fill after an overflow)
     */
    @Overwrite
    public void resetCache() {
        if (this.bons$overflow) {
            Arrays.fill(this.cache, (byte) 0);
        } else {
            byte[] c = this.cache;
            int[] d = this.bons$dirty;
            for (int i = 0, n = this.bons$dirtyCount; i < n; i++) c[d[i]] = 0;
        }
        this.bons$dirtyCount = 0;
        this.bons$overflow = false;
    }

    /**
     * @author Bons and Furious (entityculling_dirty_cache_reset)
     * @reason the original write, with the byte noted for the next reset
     */
    @Overwrite
    public void setVisible(int x, int y, int z) {
        this.positionKey = x + y * this.reachX2 + z * this.reachX2 * this.reachX2;
        this.entry = this.positionKey / 4;
        this.offset = this.positionKey % 4 * 2;
        int n = this.entry;
        this.bons$mark(n);
        this.cache[n] = (byte) (this.cache[n] | 1 << this.offset);
    }

    /**
     * @author Bons and Furious (entityculling_dirty_cache_reset)
     * @reason the original write, with the byte noted for the next reset
     */
    @Overwrite
    public void setHidden(int x, int y, int z) {
        this.positionKey = x + y * this.reachX2 + z * this.reachX2 * this.reachX2;
        this.entry = this.positionKey / 4;
        this.offset = this.positionKey % 4 * 2;
        int n = this.entry;
        this.bons$mark(n);
        this.cache[n] = (byte) (this.cache[n] | 1 << this.offset + 1);
    }

    /**
     * @author Bons and Furious (entityculling_dirty_cache_reset)
     * @reason the original write, with the byte noted for the next reset
     */
    @Overwrite
    public void setLastVisible() {
        int n = this.entry;
        this.bons$mark(n);
        this.cache[n] = (byte) (this.cache[n] | 1 << this.offset);
    }

    /**
     * @author Bons and Furious (entityculling_dirty_cache_reset)
     * @reason the original write, with the byte noted for the next reset
     */
    @Overwrite
    public void setLastHidden() {
        int n = this.entry;
        this.bons$mark(n);
        this.cache[n] = (byte) (this.cache[n] | 1 << this.offset + 1);
    }
}
