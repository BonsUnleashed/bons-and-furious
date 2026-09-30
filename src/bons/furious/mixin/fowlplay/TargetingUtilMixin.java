package bons.furious.mixin.fowlplay;

import aqario.fowlplay.common.entity.FlyingBirdEntity;
import aqario.fowlplay.common.util.CylindricalRadius;
import aqario.fowlplay.common.util.TargetingUtil;
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
@Mixin(value = TargetingUtil.class, remap = false)
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
        BlockPos adjustedPos = RandomPos.m_217863_(entity, range.horizontal(), entity.m_217043_(), pos);
        if (!bons$loaded(entity, adjustedPos)) {
            return null;
        }
        adjustedPos = shiftPosTowardsFlyHeightRange(entity, adjustedPos);
        int surfaceY = entity.m_9236_().m_6924_(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, adjustedPos.m_123341_(), adjustedPos.m_123343_());
        if (adjustedPos.m_123342_() < surfaceY && entity.m_20186_() >= surfaceY) {
            adjustedPos = adjustedPos.m_175288_(surfaceY + 12);
        } else if (entity.m_20186_() < surfaceY) {
            adjustedPos = RandomPos.m_148545_(adjustedPos, entity.m_9236_().m_151558_(),
                    currentPos -> GoalUtils.m_148461_(entity, currentPos) || GoalUtils.m_148445_(entity, currentPos));
        }
        if (GoalUtils.m_148461_(entity, adjustedPos) || GoalUtils.m_148445_(entity, adjustedPos) || GoalUtils.m_148458_(entity, adjustedPos)) {
            return null;
        }
        return adjustedPos;
    }

    /** True unless the mob is on the server and the chunk holding pos is not loaded (getChunkNow never loads). */
    @Unique
    private static boolean bons$loaded(PathfinderMob mob, BlockPos pos) {
        Level level = mob.m_9236_();
        if (!(level instanceof ServerLevel)) {
            return true;
        }
        return ((ServerLevel) level).m_7726_().m_7131_(pos.m_123341_() >> 4, pos.m_123343_() >> 4) != null;
    }
}
