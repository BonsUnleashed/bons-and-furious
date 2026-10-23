package bons.furious.mixin.vanilla_outline_memos;

import bons.furious.patch.vanilla_outline_memos.OutlineEdges;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * vanilla_outline_edge_memo (Minecraft 1.20.1 LevelRenderer, Forge 47.4.16 jar; client).
 *
 * renderShape (renderShape, the block outline of the block the player looks at, every frame) makes one call,
 * shape.forAllEdges(consumer) (forAllEdges). This wraps it (MixinExtras @WrapOperation): for the shape object drawn last, the
 * kept edges are replayed into the consumer in the walk's order; a new shape of a known immutable class is walked once into a
 * recorder and replayed; anything else walks as before. See OutlineEdges. No Minecraft code is carried.
 */
@Mixin(value = LevelRenderer.class, remap = false)
public abstract class LevelRendererOutlineEdgeMixin {
    @WrapOperation(method = "renderShape", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/phys/shapes/VoxelShape;forAllEdges(Lnet/minecraft/world/phys/shapes/Shapes$DoubleLineConsumer;)V"),
            require = 1, allow = 1)
    private static void bons$outlineEdges(VoxelShape shape, Shapes.DoubleLineConsumer consumer, Operation<Void> original) {
        OutlineEdges.forAllEdges(shape, consumer, original);
    }
}
