package bons.furious.mixin.fowlplay;

import aqario.fowlplay.common.entity.bird.FlyingBirdEntity;
import aqario.fowlplay.common.util.CylindricalRadius;
import aqario.fowlplay.common.util.TargetingUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.util.GoalUtils;
import net.minecraft.world.entity.ai.util.RandomPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

/**
 * fowlplay_air_targets_loaded_only (Fowl Play 1.1.2+1.20.1-forge).
 *
 * tryFindAir picked a random flight target and then read that column (height map plus three block checks) through
 * the level, which loads, and if needed generates, a chunk that is not loaded yet; on the server a bird aiming outside
 * the loaded area stalled the server thread until that chunk was generated. A target whose chunk is not already loaded
 * on the server is now rejected right after it is picked (null, the same answer as any other unusable target); on the
 * client and for loaded chunks nothing changes.
 */
@Mixin(value = TargetingUtils.class, remap = false)
public abstract class TargetingUtilMixin {
    @Shadow
    public static BlockPos shiftPosTowardsFlyHeightRange(FlyingBirdEntity entity, BlockPos pos) {
        throw new AssertionError();
    }

    /**
     * @author BonsUnleashed
     * @reason Reject a random air target in a chunk the server has not loaded before reading its column.
     */
    @Overwrite
    public static BlockPos tryFindAir(FlyingBirdEntity entity, CylindricalRadius range, BlockPos pos) {
        BlockPos adjustedPos = RandomPos.generateRandomPosTowardDirection(entity, range.horizontal(), entity.getRandom(), pos);
        if (!bons$loaded(entity, adjustedPos)) {
            return null;
        }
        adjustedPos = shiftPosTowardsFlyHeightRange(entity, adjustedPos);
        int surfaceY = entity.level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, adjustedPos.getX(), adjustedPos.getZ());
        if (adjustedPos.getY() < surfaceY && entity.getY() >= surfaceY) {
            adjustedPos = adjustedPos.atY(surfaceY + 12);
        } else if (entity.getY() < surfaceY) {
            adjustedPos = RandomPos.moveUpOutOfSolid(adjustedPos, entity.level().getMaxBuildHeight(),
                    currentPos -> GoalUtils.isSolid(entity, currentPos) || GoalUtils.isWater(entity, currentPos));
        }
        if (GoalUtils.isSolid(entity, adjustedPos) || GoalUtils.isWater(entity, adjustedPos) || GoalUtils.hasMalus(entity, adjustedPos)) {
            return null;
        }
        return adjustedPos;
    }

    /** True unless the mob is on the server and the chunk holding pos is not loaded (getChunkNow never loads). */
    @Unique
    private static boolean bons$loaded(PathfinderMob mob, BlockPos pos) {
        Level level = mob.level();
        if (!(level instanceof ServerLevel)) {
            return true;
        }
        return ((ServerLevel) level).getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) != null;
    }
}
