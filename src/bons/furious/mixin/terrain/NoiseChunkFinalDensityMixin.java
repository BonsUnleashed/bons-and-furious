package bons.furious.mixin.terrain;

import bons.pure.terrain.FinalDensityReuse;
import java.util.Map;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * terrain_final_density_reuse (Minecraft 1.20.1 world generation), part 3 of 3: NoiseChunk's second density pass.
 *
 * NoiseChunk's constructor maps the noise router with its wrap visitor, then maps
 * cacheAllInCell(add(finalDensity, beardifier)) with a second wrap visitor. That second visitor now goes through
 * FinalDensityReuse.pass2 together with the wrap table the first pass filled, so the mapAll methods patched by parts 1
 * and 2 can recognise nodes the first pass already produced. The first mapping (router.mapAll) is not touched.
 */
@Mixin(value = NoiseChunk.class, remap = false)
public abstract class NoiseChunkFinalDensityMixin {
    /** The wrap table: every node the wrap visitor has seen, mapped to what it became. */
    @Shadow @Final private Map<DensityFunction, DensityFunction> f_209161_;

    @ModifyArg(method = "<init>", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/levelgen/DensityFunction;m_207456_(Lnet/minecraft/world/level/levelgen/DensityFunction$Visitor;)Lnet/minecraft/world/level/levelgen/DensityFunction;"),
            require = 0)   // never refused; bons.furious.guard.CallSites decides whether the pass-2 visitor is used
    private DensityFunction.Visitor bons$secondPassVisitor(DensityFunction.Visitor visitor) {
        return FinalDensityReuse.pass2(visitor, this.f_209161_);
    }
}
