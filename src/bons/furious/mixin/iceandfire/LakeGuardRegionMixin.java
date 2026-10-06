package bons.furious.mixin.iceandfire;

import agentcraft.hostilevillages.DistantGenerationCompat;
import com.bawnorton.mixinsquared.TargetHandler;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.LakeFeature;
import net.minecraft.world.level.levelgen.feature.configurations.BlockStateConfiguration;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * iceandfire_lake_guard_region (Ice and Fire 2.1.13-beta-5).
 *
 * Ice and Fire's lake guard (NoLakesInStructuresMixin, a HEAD injection into LakeFeature.place) asks the level's
 * StructureManager whether a Mausoleum, Graveyard or Gorgon Temple starts at the lake position. During generation the
 * level is a WorldGenRegion, and the server-wide StructureManager may look outside that region, which stalls or throws
 * on Distant Horizons and C2ME worker threads. Scoping the manager to the region with forWorldGenRegion keeps the
 * lookup inside the chunks being generated; the answer for positions inside the region is unchanged.
 *
 * 1.0.34: only where the region holds every chunk the lookup reads. Ice and Fire's getStructureAt(lake position,
 * structure) reads the lake's chunk and the start chunk of every Mausoleum, Graveyard or Gorgon Temple that references it
 * (up to 8 chunks away), and a vanilla generation region reaches 8 chunks from its centre and throws ("We are asking a
 * region for a chunk out of bound") beyond that. A lake placed off the centre chunk (a jigsaw lake, e.g. Strayed Fates:
 * Forsaken's) could stop chunk generation there. Such a lake asks the live level, as Ice and Fire does; Distant Horizons'
 * region answers an empty chunk outside its bounds instead of throwing, so it stays scoped (the rule of
 * structure_gel_lake_guard_region).
 */
@Mixin(value = LakeFeature.class, priority = 1500, remap = false)
public abstract class LakeGuardRegionMixin {
    @TargetHandler(mixin = "com.github.alexthe666.iceandfire.mixin.gen.NoLakesInStructuresMixin", name = "iaf_noLakesInMausoleum")
    @ModifyExpressionValue(method = "@MixinSquared:Handler",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;m_215010_()Lnet/minecraft/world/level/StructureManager;"))
    private StructureManager bons$scopeToRegion(StructureManager manager, FeaturePlaceContext<BlockStateConfiguration> context,
                                                CallbackInfoReturnable<Boolean> cir) {
        WorldGenRegion region = (WorldGenRegion) context.m_159774_();   // Ice and Fire returns before this call otherwise
        // 1.0.34: scoped only where every chunk the lookup can read is inside the region (getCenter m_143488_)
        if (region.m_143488_().equals(new ChunkPos(context.m_159777_())) || DistantGenerationCompat.isDistantGeneration(region)) {
            return manager.m_220468_(region);
        }
        return manager;
    }
}
