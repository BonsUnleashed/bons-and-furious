package bons.furious.mixin.ryoamiclights;

import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.thinkingstudio.ryoamiclights.RyoamicLights;

/**
 * ryoamiclights_chunk_iteration, entity half (Ryoamic Lights 0.2.3+mc1.20.1).
 *
 * Ryoamic's lightsource.EntityMixin adds ryoamiclights$scheduleTrackedChunksRebuild to Entity. It walked the tracked
 * chunk set (a fastutil LongOpenHashSet) with a for-each loop, which boxes every chunk position into a Long. The same
 * method here reads the positions with LongIterator.nextLong(): the same chunks are rebuilt in the same order, without
 * creating a Long per chunk.
 *
 * How it replaces theirs: their method is a plain (non-injector) mixin method, so no injector can rewrite its loop.
 * Mixin merges mixins in priority order, and a method merged by a higher-priority mixin replaces the one with the same
 * name and descriptor that a lower-priority mixin merged before it (theirs: default 1000, this one: 1500).
 */
@Mixin(value = Entity.class, priority = 1500, remap = false)
public abstract class EntityChunkRebuildMixin {
    @Shadow
    private Level f_19853_;

    /**
     * Ryoamic's field, added to Entity by their EntityMixin. Deliberately not @Shadow: Mixin resolves @Shadow fields
     * before any mixin is merged, when the field does not exist yet ("@Shadow field ... was not located"). A plain
     * field is added only if the target lacks it at merge time; theirs is merged first, so this declaration is dropped
     * and the method below reads their field.
     */
    private LongOpenHashSet ryoamiclights$trackedLitChunkPos;

    /** Same as Ryoamic's method, with nextLong() instead of the boxing for-each loop. */
    public void ryoamiclights$scheduleTrackedChunksRebuild(LevelRenderer renderer) {
        if (Minecraft.m_91087_().f_91073_ == this.f_19853_) {
            LongIterator iterator = this.ryoamiclights$trackedLitChunkPos.iterator();
            while (iterator.hasNext()) {
                long pos = iterator.nextLong();
                RyoamicLights.scheduleChunkRebuild(renderer, pos);
            }
        }
    }
}
