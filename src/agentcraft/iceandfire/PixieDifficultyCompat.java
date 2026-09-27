package agentcraft.iceandfire;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.level.WorldGenLevel;

/** Keep Pixie's difficulty query inside the WorldGenRegion's actual base bounds. */
public final class PixieDifficultyCompat {
    private PixieDifficultyCompat() {}

    public static DifficultyInstance get(WorldGenLevel level, BlockPos position) {
        if (level instanceof WorldGenRegion region
                && !region.m_7232_(position.m_123341_() >> 4, position.m_123343_() >> 4)) {
            // Vanilla WorldGenRegion uses the same difficulty/daytime/moon values
            // and zero inhabited time at every position. DH can supply extra
            // temporary chunks beyond the bounds inherited by this one query.
            // The region's own center is always part of its original chunk list.
            position = region.m_143488_().m_45615_();
        }
        return level.m_6436_(position);
    }
}
