package bons.furious.mixin.mekanism_radiation;

import bons.furious.patch.mekanism_radiation.RadiationSourceRange;
import com.google.common.collect.Table;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.Set;
import mekanism.api.Chunk3D;
import mekanism.common.lib.radiation.RadiationManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * mekanism_radiation_source_range (Mekanism 1.20.1-10.4.13.69, logical server incl. the integrated server):
 * RadiationManager.getRadiationLevelAndMaxMagnitude(Coord4D), its one call new Chunk3D(coord).expand(radius). The call
 * is answered with the chunks of expand's set that hold a row of radiationTable when there are none or exactly one
 * (RadiationSourceRange); the method's own loop, additions and result construction run unchanged, so the result is
 * bit-identical (see RadiationSourceRange). Valkyrien Skies' MixinRadiationManager (@ModifyVariable in radiate) is in
 * another method and unaffected. require = 0: a mod that rewrites the method leaves the switch out.
 */
@Mixin(value = RadiationManager.class, remap = false)
public abstract class RadiationManagerSourceRangeMixin {
    @Shadow
    @Final
    private Table<Chunk3D, ?, ?> radiationTable;

    @WrapOperation(method = "getRadiationLevelAndMaxMagnitude(Lmekanism/api/Coord4D;)Lmekanism/common/lib/radiation/RadiationManager$LevelAndMaxMagnitude;",
            require = 0, at = @At(value = "INVOKE", target = "Lmekanism/api/Chunk3D;expand(I)Ljava/util/Set;"))
    private Set<Chunk3D> bons$sourceChunks(Chunk3D centre, int radius, Operation<Set<Chunk3D>> original) {
        return RadiationSourceRange.chunksToVisit(radiationTable, centre, radius, original);
    }
}
