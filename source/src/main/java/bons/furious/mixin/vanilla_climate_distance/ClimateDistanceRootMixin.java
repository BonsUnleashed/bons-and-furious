package bons.furious.mixin.vanilla_climate_distance;

import bons.furious.patch.vanilla_climate_distance.ClimateDistanceReuse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * vanilla_climate_search_distance_reuse (Minecraft 1.20.1, every multi-noise biome lookup, both sides; since 1.0.46).
 * Idea: Moonrise's "Optimise Climate RTree lookup" commit text (idea text only); the design is ours.
 *
 * Climate.RTree.search (search) makes one call into its tree: root.search(target, lastLeaf, metric) (Node.search).
 * This redirect hands that one call to ClimateDistanceReuse, which walks the tree as SubTree.search does but reuses the
 * distances the walk already knows (the candidate's and every found leaf's), for metrics proven to be vanilla's node
 * distance; anything else runs the original call. SubTree.search and Leaf.search themselves are not changed, so the
 * fingerprints vanilla_climate_search_repeat guards still describe the code that runs. The tree's node types are not
 * public, hence the coerced parameters and return value (Mixin casts the result back to the leaf type).
 */
@Mixin(targets = "net.minecraft.world.level.biome.Climate$RTree", remap = false)
public abstract class ClimateDistanceRootMixin {
    @Coerce
    @Redirect(method = "search", remap = false, at = @At(value = "INVOKE", remap = false,
            target = "Lnet/minecraft/world/level/biome/Climate$RTree$Node;search([JLnet/minecraft/world/level/biome/Climate$RTree$Leaf;Lnet/minecraft/world/level/biome/Climate$DistanceMetric;)Lnet/minecraft/world/level/biome/Climate$RTree$Leaf;"))
    private Object bons$searchWithKnownDistances(@Coerce Object root, long[] target, @Coerce Object candidate, @Coerce Object metric) {
        return ClimateDistanceReuse.search(root, target, candidate, metric);
    }
}
