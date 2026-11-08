package bons.furious.mixin.lithostitched_c49;

import bons.furious.patch.lithostitched_c49.TemplateShuffle;
import dev.worldgen.lithostitched.worldgen.structure.LithostitchedTemplates;
import java.util.List;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * lithostitched_template_shuffle_arrays (Lithostitched 1.8.1 for NeoForge 1.21.1, MIT; server side): a cancellable HEAD inject
 * on LithostitchedTemplates.shuffle(RandomSource) answers with TemplateShuffle.shuffle (the same order and the same RNG draws
 * from plain arrays; why identical: its javadoc). An injection rather than an @Overwrite, so other mods' injectors into
 * shuffle keep their target. Switch off (-Dbons_and_furious.lithostitchedTemplateShuffleArrays=false): Lithostitched's body.
 */
@Mixin(value = LithostitchedTemplates.class, remap = false)
public abstract class TemplatesShuffleMixin {
    @Shadow
    @Final
    protected List<LithostitchedTemplates.WeightedEntry> entries;

    @Inject(method = "shuffle(Lnet/minecraft/util/RandomSource;)Ljava/util/List;", at = @At("HEAD"), cancellable = true)
    private void bons$lsShuffle(RandomSource random, CallbackInfoReturnable<List<StructurePoolElement>> cir) {
        if (TemplateShuffle.enabled) cir.setReturnValue(TemplateShuffle.shuffle(this.entries, random));
    }
}
