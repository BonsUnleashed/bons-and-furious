package agentcraft.pure;

import java.util.function.Predicate;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;

/** Conservative section-palette bound; retains AmbientSounds' exact column scan. */
public final class AcAmbientHeight {
    public static volatile boolean enabled = Boolean.parseBoolean(System.getProperty("bons_and_furious.ambientHeight", "true"));
    // canOcclude is necessary for vanilla solid-render. The installed A Good Place
    // hook only changes true to false. Live tags and waterlogged AIR are included.
    private static final Predicate<BlockState> MAY_MATCH = state -> state.m_60815_()
            || state.m_204336_(BlockTags.f_13035_)
            || state.m_60819_().m_205070_(FluidTags.f_13131_);

    public static int start(int original, Level level, BlockPos.MutableBlockPos pos) {
        if (!enabled || level.getClass() != ClientLevel.class) return original;
        int min = level.m_141937_(), x = pos.m_123341_(), z = pos.m_123343_();
        if (original <= min || x < -30000000 || x >= 30000000 || z < -30000000 || z >= 30000000
                || !level.m_7232_(x >> 4, z >> 4)
                || MAY_MATCH.test(Blocks.f_50626_.m_49966_())) return original;
        LevelChunkSection[] sections = level.m_6325_(x >> 4, z >> 4).m_7103_();
        for (int i = sections.length - 1; i >= 0; i--) {
            LevelChunkSection section = sections[i];
            if (section == null ? MAY_MATCH.test(Blocks.f_50016_.m_49966_()) : section.m_63002_(MAY_MATCH)) {
                int top = ((level.m_151560_() + i) << 4) + 15;
                return Math.max(min + 1, Math.min(original, top));
            }
        }
        // The old loop excludes minY, returns 0 on no match, and leaves pos at minY+1.
        return min + 1;
    }
}
