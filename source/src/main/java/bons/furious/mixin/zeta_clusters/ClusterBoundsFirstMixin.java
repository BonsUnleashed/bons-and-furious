package bons.furious.mixin.zeta_clusters;

import bons.furious.patch.zeta_clusters.ClusterBoundsFirst;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.violetmoon.zeta.world.generator.multichunk.ClusterBasedGenerator;
import org.violetmoon.zeta.world.generator.multichunk.ClusterShape;

/**
 * zeta_cluster_bounds_first (Zeta 1.0-24 with Quark 4.0-460, world generation, both sides; since 1.0.46). Idea: ours.
 *
 * ClusterBasedGenerator.generateChunkPart hands Zeta's forEachChunkBlock a lambda (lambda$generateChunkPart$0) that, for
 * each visited position, does "if (context.canPlaceAt(pos) && shape.isInside(pos)) context.consume(pos)". This redirect
 * replaces only the canPlaceAt call: ClusterBoundsFirst answers false for a position outside the cluster's ellipsoid when
 * the context is Quark's pure big-stone context, and otherwise asks the context as before. The lambda's own arguments
 * (context, shape, pos) arrive as trailing handler parameters. Zeta and Quark are CC-BY-NC-SA-3.0: none of their code is
 * carried here; the ellipsoid value is the cluster's defining expression, written from its behaviour.
 */
@Mixin(value = ClusterBasedGenerator.class, remap = false)
public abstract class ClusterBoundsFirstMixin {
    @Redirect(method = "lambda$generateChunkPart$0", remap = false, at = @At(value = "INVOKE", remap = false,
            target = "Lorg/violetmoon/zeta/world/generator/multichunk/ClusterBasedGenerator$IGenerationContext;canPlaceAt(Lnet/minecraft/core/BlockPos;)Z"))
    private static boolean bons$shapeFirst(ClusterBasedGenerator.IGenerationContext context, BlockPos pos,
                                           ClusterBasedGenerator.IGenerationContext lambdaContext, ClusterShape shape, BlockPos lambdaPos) {
        return ClusterBoundsFirst.canPlaceAt(context, pos, shape);
    }
}
