package bons.furious.mixin.terrain;

import bons.pure.terrain.FinalDensityReuse;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * terrain_final_density_reuse (Minecraft 1.20.1 world generation), part 1 of 3: six density function records.
 *
 * NoiseChunk maps its final density a second time with a fresh wrap visitor, and for these records mapAll then rebuilds
 * each node from its re-mapped children and looks the copy up in the wrap table, only to get back the node the first
 * pass made. FinalDensityReuse.reuse tells, before any of that work, whether vanilla would return this very node; if so
 * mapAll returns it at once, otherwise the original method runs unchanged. The early return needs an @Inject; mapAll
 * runs once per graph node while a NoiseChunk is built, never per density sample.
 */
// Spline is public; the other five records are not, so they are named by string.
@Mixin(value = DensityFunctions.Spline.class, targets = {
        "net.minecraft.world.level.levelgen.DensityFunctions$Ap2",
        "net.minecraft.world.level.levelgen.DensityFunctions$BlendDensity",
        "net.minecraft.world.level.levelgen.DensityFunctions$RangeChoice",
        "net.minecraft.world.level.levelgen.DensityFunctions$ShiftedNoise",
        "net.minecraft.world.level.levelgen.DensityFunctions$WeirdScaledSampler"}, remap = false)
public abstract class DensityRecordReuseMixin {
    /** mapAll: return this node unchanged when the second pass would get the same object back. */
    @Inject(method = "m_207456_", at = @At("HEAD"), cancellable = true)
    private void bons$returnUnchanged(DensityFunction.Visitor visitor, CallbackInfoReturnable<DensityFunction> cir) {
        DensityFunction self = (DensityFunction) (Object) this;
        if (FinalDensityReuse.reuse(self, visitor)) cir.setReturnValue(self);
    }
}
