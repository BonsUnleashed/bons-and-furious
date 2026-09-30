package bons.furious.mixin.structure_gel;

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
 * The manager is now scoped to the region with StructureManager.forWorldGenRegion when the level is a WorldGenRegion;
 * the answer and the lake-proof test are unchanged.
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
        if (level instanceof WorldGenRegion region) {
            structureManager = structureManager.m_220468_(region);
        }
        return !structureManager.m_220477_(new ChunkPos(context.m_159777_()),
                structure -> StructureAccessHelper.isLakeProof(context.m_159774_().m_9598_(), structure)).isEmpty();
    }
}
