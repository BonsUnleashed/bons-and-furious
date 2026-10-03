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
    private static final Predicate<BlockState> MAY_MATCH = state -> state.canOcclude()
            || state.is(BlockTags.LEAVES)
            || state.getFluidState().is(FluidTags.WATER);

    public static int start(int original, Level level, BlockPos.MutableBlockPos pos) {
        if (!enabled || level.getClass() != ClientLevel.class) return original;
        int min = level.getMinBuildHeight(), x = pos.getX(), z = pos.getZ();
        if (original <= min || x < -30000000 || x >= 30000000 || z < -30000000 || z >= 30000000
                || !level.hasChunk(x >> 4, z >> 4)
                || MAY_MATCH.test(Blocks.VOID_AIR.defaultBlockState())) return original;
        LevelChunkSection[] sections = level.getChunk(x >> 4, z >> 4).getSections();
        for (int i = sections.length - 1; i >= 0; i--) {
            LevelChunkSection section = sections[i];
            if (section == null ? MAY_MATCH.test(Blocks.AIR.defaultBlockState()) : section.maybeHas(MAY_MATCH)) {
                int top = ((level.getMinSection() + i) << 4) + 15;
                return Math.max(min + 1, Math.min(original, top));
            }
        }
        // The old loop excludes minY, returns 0 on no match, and leaves pos at minY+1.
        return min + 1;
    }
}
