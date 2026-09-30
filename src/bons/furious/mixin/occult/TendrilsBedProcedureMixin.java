package bons.furious.mixin.occult;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.eventbus.api.Event;
import occult.init.OccultModEntities;
import occult.procedures.TendrilsBedProcedure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

/**
 * occult_bed_scan_guard (Occult Alpha 5).
 *
 * TendrilsBedProcedure scanned the 3,071 positions of a 19x19x19 sphere around every player on every player tick, on
 * the client as well as the server, reading each block through the level (which loads, or generates, a chunk that is
 * not ready) and allocating a tag id per position. The scan now runs only on the server, takes the procedure's own
 * 1-in-1000 random draw before reading anything, reads the block only from an already loaded chunk, and uses the
 * vanilla beds tag directly. A Tendrils still spawns under the same conditions at a loaded bed.
 */
@Mixin(value = TendrilsBedProcedure.class, remap = false)
public abstract class TendrilsBedProcedureMixin {
    /**
     * @author BonsUnleashed
     * @reason Server-only bed scan that never loads chunks and draws the spawn chance before reading blocks.
     */
    @Overwrite
    private static void execute(Event event, LevelAccessor world, double x, double y, double z) {
        if (!(world instanceof ServerLevel)) {
            return;
        }
        ServerLevel serverLevel = (ServerLevel) world;
        ServerChunkCache chunkSource = serverLevel.m_7726_();
        int horizontalRadiusSphere = 9;
        int verticalRadiusSphere = 9;
        int yIterationsSphere = verticalRadiusSphere;
        for (int i = -yIterationsSphere; i <= yIterationsSphere; i++) {
            for (int xi = -horizontalRadiusSphere; xi <= horizontalRadiusSphere; xi++) {
                for (int zi = -horizontalRadiusSphere; zi <= horizontalRadiusSphere; zi++) {
                    double distanceSq = (double) (xi * xi) / (double) (horizontalRadiusSphere * horizontalRadiusSphere)
                            + (double) (i * i) / (double) (verticalRadiusSphere * verticalRadiusSphere)
                            + (double) (zi * zi) / (double) (horizontalRadiusSphere * horizontalRadiusSphere);
                    if (distanceSq <= 1.0 && Math.random() < 0.001) {
                        BlockPos pos = BlockPos.m_274561_(x + xi, y + i, z + zi);
                        LevelChunk chunk = chunkSource.m_7131_(pos.m_123341_() >> 4, pos.m_123343_() >> 4);
                        if (chunk != null && chunk.m_8055_(pos).m_204336_(BlockTags.f_13038_)
                                && serverLevel.m_45517_(LightLayer.BLOCK, pos) <= 6) {
                            Entity entityToSpawn = OccultModEntities.TENDRILS.get().m_262496_(serverLevel, pos, MobSpawnType.MOB_SUMMONED);
                            if (entityToSpawn != null) {
                                entityToSpawn.m_20334_(0.0, 0.0, 0.0);
                            }
                        }
                    }
                }
            }
        }
    }
}
