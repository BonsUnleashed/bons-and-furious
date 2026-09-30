package bons.furious.mixin.iceandfire;

import com.bawnorton.mixinsquared.TargetHandler;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.server.level.WorldGenRegion;
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
 */
@Mixin(value = LakeFeature.class, priority = 1500, remap = false)
public abstract class LakeGuardRegionMixin {
    @TargetHandler(mixin = "com.github.alexthe666.iceandfire.mixin.gen.NoLakesInStructuresMixin", name = "iaf_noLakesInMausoleum")
    @ModifyExpressionValue(method = "@MixinSquared:Handler",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;m_215010_()Lnet/minecraft/world/level/StructureManager;"))
    private StructureManager bons$scopeToRegion(StructureManager manager, FeaturePlaceContext<BlockStateConfiguration> context,
                                                CallbackInfoReturnable<Boolean> cir) {
        return manager.m_220468_((WorldGenRegion) context.m_159774_());
    }
}
