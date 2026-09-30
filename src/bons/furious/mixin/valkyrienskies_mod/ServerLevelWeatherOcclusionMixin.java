package bons.furious.mixin.valkyrienskies_mod;

import com.bawnorton.mixinsquared.TargetHandler;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Position;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.valkyrienskies.mod.common.util.AcVsSweep6;

/**
 * valkyrien_weather_occlusion (Valkyrien Skies 2.4.11), a fix.
 *
 * When a ship chunk's surface lies under world terrain, VS's world_weather MixinServerLevel.occlude returns the marker
 * BlockPos(0, minBuildHeight - 1, 0), and useBiomeAtWorldPos pushed that marker through the ship transform and read
 * the biome about 28 million blocks away, in a chunk that is never loaded, sampling the full climate noise every time.
 * occlude now records the marker it returns (AcVsSweep6.markOccluded), and useBiomeAtWorldPos reads the biome for
 * exactly that marker in the middle of the ticking chunk (AcVsSweep6.occludedProbe; the biome is not observable below
 * the world). Both are MixinSquared injections into VS's handlers, without a CallbackInfo.
 */
@Mixin(value = ServerLevel.class, priority = 1500, remap = false)
public abstract class ServerLevelWeatherOcclusionMixin {
    /** occlude's first return statement is "return failure" (the ship column is under world terrain). */
    @TargetHandler(mixin = "org.valkyrienskies.mod.mixin.feature.world_weather.MixinServerLevel", name = "occlude")
    @ModifyReturnValue(method = "@MixinSquared:Handler", at = @At(value = "RETURN", ordinal = 0))
    private BlockPos bons$markOccluded(BlockPos failure, @Local(argsOnly = true) LevelChunk chunk) {
        return AcVsSweep6.markOccluded(failure, chunk);
    }

    /**
     * useBiomeAtWorldPos passes BlockPos.containing(pos moved to world space) to getBiome; for the marker occlude just
     * returned, the probe position is passed instead (Mixin passes the handler's first two arguments after the
     * position). The 1.0.19 handler also required the shared ship reference to be set; occlude sets it to a ship before
     * it can mark a position, and the marker is a new object used only by that tickChunk call, so a matching marker
     * always comes with a ship (the equivalence argument is in notes/valkyrienskies_mod.md).
     */
    @TargetHandler(mixin = "org.valkyrienskies.mod.mixin.feature.world_weather.MixinServerLevel", name = "useBiomeAtWorldPos")
    @Redirect(method = "@MixinSquared:Handler",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/core/BlockPos;m_274446_(Lnet/minecraft/core/Position;)Lnet/minecraft/core/BlockPos;"))
    private BlockPos bons$probeInTickingChunk(Position worldPos, ServerLevel level, BlockPos pos) {
        BlockPos probe = AcVsSweep6.occludedProbe(pos);
        if (probe != null) {
            return probe;
        }
        return BlockPos.m_274446_(worldPos);
    }
}
