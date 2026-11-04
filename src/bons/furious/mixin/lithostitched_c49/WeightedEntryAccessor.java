package bons.furious.mixin.lithostitched_c49;

import dev.worldgen.lithostitched.worldgen.structure.LithostitchedTemplates;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * lithostitched_template_shuffle_arrays (Lithostitched 1.4.11): read access to a weighted entry's element and weight (both
 * package-private finals) for bons.furious.patch.lithostitched_c49.TemplateShuffle. Adds no state, changes nothing.
 */
@Mixin(value = LithostitchedTemplates.WeightedEntry.class, remap = false)
public interface WeightedEntryAccessor {
    @Accessor("element")
    StructurePoolElement bons$lsElement();

    @Accessor("weight")
    int bons$lsWeight();
}
