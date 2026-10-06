package bons.furious.mixin.structure_gel;

import agentcraft.hostilevillages.DistantGenerationCompat;
import com.legacy.structure_gel.api.structure.StructureAccessHelper;
import com.legacy.structure_gel.core.asm_hooks.LakeFeatureHooks;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

/**
 * structure_gel_lake_guard_region (Structure Gel API 2.16.2).
 *
 * Structure Gel keeps lakes out of its structures: its LakeFeature hook asks whether a lake-proof structure starts in
 * the lake's chunk. It asked the live ServerLevel's StructureManager even while the lake was being placed in a
 * generation region, so the check could force live chunks to load on the server thread (a Distant Horizons freeze).
 * The manager is now scoped to the region with StructureManager.forWorldGenRegion when the level is a WorldGenRegion
 * centred on the lake's chunk, or Distant Horizons' generation region; the answer and the lake-proof test are unchanged.
 *
 * 1.0.34: only then. The scoped lookup reads the start chunk of every lake-proof structure that references the lake's
 * chunk (up to 8 chunks away from it), and a vanilla generation region reaches 8 chunks from its centre and throws ("We
 * are asking a region for a chunk out of bound") beyond that. A lake placed off the centre chunk (Strayed Fates:
 * Forsaken's jigsaw lakes are offset by -8 blocks) next to a large lake-proof structure such as a stronghold could stop
 * chunk generation there. Such a lake asks the live level, as Structure Gel does; Distant Horizons' region answers an
 * empty chunk outside its bounds instead of throwing, so it stays scoped as before.
 */
@Mixin(value = LakeFeatureHooks.class, remap = false)
public abstract class LakeFeatureHooksMixin {
    /**
     * @author BonsUnleashed
     * @reason Look the structure starts up through the generating region instead of the live level.
     */
    @Overwrite
    public static boolean checkForStructures(FeaturePlaceContext<?> context) {
        WorldGenLevel level = context.m_159774_();
        StructureManager structureManager = level.m_6018_().m_215010_();
        ChunkPos lakeChunk = new ChunkPos(context.m_159777_());
        // 1.0.34: scoped only where every chunk the lookup can read is inside the region (getCenter m_143488_)
        if (level instanceof WorldGenRegion region
                && (region.m_143488_().equals(lakeChunk) || DistantGenerationCompat.isDistantGeneration(region))) {
            structureManager = structureManager.m_220468_(region);
        }
        return !structureManager.m_220477_(lakeChunk,
                structure -> StructureAccessHelper.isLakeProof(context.m_159774_().m_9598_(), structure)).isEmpty();
    }
}
