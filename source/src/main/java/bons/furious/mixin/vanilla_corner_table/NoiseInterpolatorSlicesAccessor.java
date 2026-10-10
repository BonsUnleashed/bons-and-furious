package bons.furious.mixin.vanilla_corner_table;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * vanilla_noise_corner_share (Minecraft 1.21.1 world generation, tested build NeoForge 21.1.252; both sides): read access
 * to a NoiseInterpolator's two slice arrays (slice0, slice1), which NoiseChunkCornerShareMixin's slice fill writes exactly
 * as the original fillSlice does. Accessors only; nothing about the class changes.
 *
 * Ported to 1.21.1: NoiseChunk$NoiseInterpolator still holds double[][] slice0 / slice1; Mojang names.
 */
@Mixin(targets = "net.minecraft.world.level.levelgen.NoiseChunk$NoiseInterpolator", remap = false)
public interface NoiseInterpolatorSlicesAccessor {
    @Accessor("slice0")
    double[][] bons$csSlice0();

    @Accessor("slice1")
    double[][] bons$csSlice1();
}
