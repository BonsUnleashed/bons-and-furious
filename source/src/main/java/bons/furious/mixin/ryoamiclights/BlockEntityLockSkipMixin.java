package bons.furious.mixin.ryoamiclights;

import bons.furious.patch.ryoamiclights.IdentityEquality;
import java.util.Set;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.thinkingstudio.ryoamiclights.DynamicLightSource;
import org.thinkingstudio.ryoamiclights.DynamicLightsConfig;
import org.thinkingstudio.ryoamiclights.RyoamicLights;

/**
 * ryoamiclights_block_entity_lock_skip (Ryoamic Lights 0.2.3+mc1.20.1, client).
 *
 * With block-entity light sources enabled, every ticking block entity asks containsLightSource twice per client tick,
 * and each call takes and releases the global read lock to look in the light-source set. A block entity can only be in
 * that set after addLightSource added it, so a sticky flag now records that any block entity is about to be added (set
 * before the write lock). While it is clear, containsLightSource answers false for a block entity whose class compares
 * by identity without taking the lock. The world check still runs first; entities, and everything after the first
 * block-entity light source, keep the original path. Both methods are otherwise Ryoamic Lights' own (MIT).
 */
@Mixin(value = RyoamicLights.class, remap = false)
public abstract class BlockEntityLockSkipMixin {
    @Shadow
    @Final
    public DynamicLightsConfig config;

    @Shadow
    @Final
    private Set<DynamicLightSource> dynamicLightSources;

    @Shadow
    @Final
    private ReentrantReadWriteLock lightSourcesLock;

    /** Set, before the write lock, whenever a block entity is about to be added; never cleared. */
    @Unique
    private volatile boolean bons$blockEntitySourceAdded;

    /**
     * @author BonsUnleashed
     * @reason Record that a block entity is about to be added (the only change; the rest is the original method).
     */
    @Overwrite
    public void addLightSource(DynamicLightSource lightSource) {
        if (!lightSource.ryoamicLights$getDynamicLightWorld().isClientSide()) {
            return;
        }
        if (!this.config.getDynamicLightsMode().isEnabled()) {
            return;
        }
        if (this.containsLightSource(lightSource)) {
            return;
        }
        if (lightSource instanceof BlockEntity) {
            this.bons$blockEntitySourceAdded = true;
        }
        this.lightSourcesLock.writeLock().lock();
        this.dynamicLightSources.add(lightSource);
        this.lightSourcesLock.writeLock().unlock();
    }

    /**
     * @author BonsUnleashed
     * @reason Answer false without the lock for a block entity while no block entity has ever been added.
     */
    @Overwrite
    public boolean containsLightSource(DynamicLightSource lightSource) {
        if (!lightSource.ryoamicLights$getDynamicLightWorld().isClientSide()) {
            return false;
        }
        if (!this.bons$blockEntitySourceAdded && lightSource instanceof BlockEntity
                && IdentityEquality.holdsFor(lightSource.getClass())) {
            return false;
        }
        this.lightSourcesLock.readLock().lock();
        boolean result = this.dynamicLightSources.contains(lightSource);
        this.lightSourcesLock.readLock().unlock();
        return result;
    }
}
