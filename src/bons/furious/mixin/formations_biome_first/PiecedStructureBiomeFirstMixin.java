package bons.furious.mixin.formations_biome_first;

import bons.furious.patch.formations_biome_first.BiomeFirst;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import com.supermartijn642.formations.structure.PiecedStructure;
import com.supermartijn642.formations.structure.StructurePlacement;
import java.util.Optional;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * formations_structure_biome_first (Formations 1.0.4, All Rights Reserved; server side; off by default): MixinExtras
 * @WrapOperation on the StructurePlacement.findHeight call of PiecedStructure.findGenerationPoint (m_214086_), whose stub
 * stands at the centre of the start piece's box (see BiomeFirst); a RETURN @Inject checks the stub in verify mode. No
 * Formations code is carried: our own logic only.
 */
@Mixin(value = PiecedStructure.class, remap = false)
public abstract class PiecedStructureBiomeFirstMixin {
    @WrapOperation(method = "m_214086_", require = 1, allow = 1, at = @At(value = "INVOKE",
            target = "Lcom/supermartijn642/formations/structure/StructurePlacement;findHeight(Lnet/minecraft/world/level/levelgen/structure/Structure$GenerationContext;Lnet/minecraft/world/level/levelgen/structure/BoundingBox;)Ljava/util/Optional;"))
    private Optional<Integer> bons$formationsBiomeFirst(StructurePlacement placement, Structure.GenerationContext context, BoundingBox box,
            Operation<Optional<Integer>> original, @Local(ordinal = 0) StructurePoolElement element,
            @Share("bons$fbf") LocalRef<BiomeFirst.Prediction> prediction) {
        BiomeFirst.Prediction p = null;
        if (BiomeFirst.VERIFY) prediction.set(p = new BiomeFirst.Prediction());
        return BiomeFirst.pieced(placement, context, box, original, element, p);
    }

    @Inject(method = "m_214086_", require = 1, at = @At("RETURN"))
    private void bons$formationsBiomeFirstVerify(Structure.GenerationContext context, CallbackInfoReturnable<Optional<Structure.GenerationStub>> cir,
            @Share("bons$fbf") LocalRef<BiomeFirst.Prediction> prediction) {
        if (BiomeFirst.VERIFY) BiomeFirst.verify(context, cir.getReturnValue(), prediction.get(), "Formations pieced structure");
    }
}
