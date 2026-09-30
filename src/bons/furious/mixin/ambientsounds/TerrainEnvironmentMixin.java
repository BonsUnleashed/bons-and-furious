package bons.furious.mixin.ambientsounds;

import agentcraft.pure.AcAmbientHeight;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import team.creative.ambientsounds.environment.TerrainEnvironment;

/**
 * ambientsounds_terrain_scan_bound (AmbientSounds 6.3.8, client).
 *
 * getHeightBlock scans a column from the build ceiling down to the first solid, leaves or water block, and the
 * terrain analysis runs it for 121 columns on a recurring cadence. AcAmbientHeight.start checks the chunk's section
 * palettes from the top and returns the highest y that could contain a match (or the original ceiling when it cannot
 * tell), so the unchanged loop below starts there instead of at the ceiling. The result is the same height.
 */
@Mixin(value = TerrainEnvironment.class, remap = false)
public abstract class TerrainEnvironmentMixin {
    /**
     * @author BonsUnleashed
     * @reason Start the unchanged column scan at the highest section that can contain a match.
     */
    @Overwrite
    public static int getHeightBlock(Level level, BlockPos.MutableBlockPos pos) {
        int heighest = 0;
        for (int y = AcAmbientHeight.start(level.m_151558_(), level, pos); y > level.m_141937_(); y--) {
            pos.m_142448_(y);
            BlockState state = level.m_8055_(pos);
            if (state.m_60804_(level, pos) || state.m_204336_(BlockTags.f_13035_) || level.m_6425_(pos).m_205070_(FluidTags.f_13131_)) {
                heighest = y;
                break;
            }
        }
        return heighest;
    }
}
