package bons.furious.mixin.ryoamiclights;

import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.thinkingstudio.ryoamiclights.RyoamicLights;

/**
 * ryoamiclights_chunk_iteration, block entity half (Ryoamic Lights 0.2.3+mc1.20.1).
 *
 * Ryoamic's lightsource.BlockEntityMixin adds ryoamiclights$scheduleTrackedChunksRebuild to BlockEntity. It walked the
 * tracked chunk set (a fastutil LongOpenHashSet) with a for-each loop, which boxes every chunk position into a Long.
 * The same method here reads the positions with LongIterator.nextLong(): the same chunks are rebuilt in the same
 * order, without creating a Long per chunk.
 *
 * How it replaces theirs: see EntityChunkRebuildMixin (a higher-priority mixin's method replaces the same-named
 * method a lower-priority mixin merged; theirs: default 1000, this one: 1500).
 */
@Mixin(value = BlockEntity.class, priority = 1500, remap = false)
public abstract class BlockEntityChunkRebuildMixin {
    @Shadow
    protected Level level;

    /**
     * Ryoamic's field, added to BlockEntity by their BlockEntityMixin. Deliberately not @Shadow (see
     * EntityChunkRebuildMixin): theirs is merged first, so this declaration is dropped and the method reads theirs.
     */
    private LongOpenHashSet ryoamiclights$trackedLitChunkPos;

    /** Same as Ryoamic's method, with nextLong() instead of the boxing for-each loop. */
    public void ryoamiclights$scheduleTrackedChunksRebuild(LevelRenderer renderer) {
        if (this.level == Minecraft.getInstance().level) {
            LongIterator iterator = this.ryoamiclights$trackedLitChunkPos.iterator();
            while (iterator.hasNext()) {
                long pos = iterator.nextLong();
                RyoamicLights.scheduleChunkRebuild(renderer, pos);
            }
        }
    }
}
