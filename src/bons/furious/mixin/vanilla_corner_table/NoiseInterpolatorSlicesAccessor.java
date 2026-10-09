package bons.furious.mixin.vanilla_corner_table;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * vanilla_noise_corner_share (Minecraft 1.20.1 world generation, tested build 1.20.1 SRG; both sides): read access to a
 * NoiseInterpolator's two slice arrays (slice0 f_188828_, slice1 f_188829_), which NoiseChunkCornerShareMixin's slice
 * fill writes exactly as the original fillSlice does. Accessors only; nothing about the class changes.
 */
@Mixin(targets = "net.minecraft.world.level.levelgen.NoiseChunk$NoiseInterpolator", remap = false)
public interface NoiseInterpolatorSlicesAccessor {
    @Accessor("f_188828_")
    double[][] bons$csSlice0();

    @Accessor("f_188829_")
    double[][] bons$csSlice1();
}
