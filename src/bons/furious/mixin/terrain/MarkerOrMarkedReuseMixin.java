package bons.furious.mixin.terrain;

import bons.furious.patch.terrain.DensityMarkers;
import bons.pure.terrain.FinalDensityReuse;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

/**
 * terrain_final_density_reuse (Minecraft 1.20.1 world generation), part 2 of 3: markers and NoiseChunk's caches.
 *
 * Markers and NoiseChunk's caches inherit mapAll from this interface. In the second density pass a cache the first pass
 * created from its marker now comes back unchanged instead of being unwrapped, re-mapped and looked up again (the same
 * check as DensityRecordReuseMixin). Mixin 0.8.5 cannot inject into interface methods, so the default method is
 * replaced; past the check it does what vanilla does: wrap the mapped input in a marker of the same type and hand that
 * to the visitor.
 */
@Mixin(value = DensityFunctions.MarkerOrMarked.class, remap = false)
public interface MarkerOrMarkedReuseMixin extends DensityFunctions.MarkerOrMarked {
    /**
     * @author BonsUnleashed
     * @reason Return an unchanged NoiseChunk cache in the second density pass; injectors cannot target an interface.
     */
    @Overwrite
    default DensityFunction m_207456_(DensityFunction.Visitor visitor) {
        if (FinalDensityReuse.reuse(this, visitor)) return this;
        return visitor.m_214017_(DensityMarkers.create(this.m_207136_(), this.m_207056_().m_207456_(visitor)));
    }
}
