package bons.furious.mixin.iceandfire;

import agentcraft.iceandfire.PixieDifficultyCompat;
import com.github.alexthe666.iceandfire.world.gen.WorldGenPixieVillage;
import net.minecraft.core.BlockPos;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.level.WorldGenLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * iceandfire_pixie_village_difficulty (Ice and Fire 2.1.13-beta-5).
 *
 * WorldGenPixieVillage.place spawns a pixie at each house and asks the generating level for the local difficulty there.
 * Distant Horizons hands the feature extra temporary chunks around a region, but WorldGenRegion.getCurrentDifficultyAt
 * checks the base region's bounds and throws for them, so pixie villages failed to generate with an exception. This
 * one call now goes through PixieDifficultyCompat.get, which reads the difficulty at the region's centre when the
 * position is outside the region (a vanilla region reports the same difficulty at every position).
 */
@Mixin(value = WorldGenPixieVillage.class, remap = false)
public abstract class PixieVillageDifficultyMixin {
    @Redirect(method = "m_142674_",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/WorldGenLevel;m_6436_(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/DifficultyInstance;"))
    private DifficultyInstance bons$difficultyInsideRegion(WorldGenLevel level, BlockPos position) {
        return PixieDifficultyCompat.get(level, position);
    }
}
