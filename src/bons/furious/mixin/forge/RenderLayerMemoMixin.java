package bons.furious.mixin.forge;

import bons.furious.patch.forge.RenderLayerMemo;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.ChunkRenderTypeSet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * forge_render_layer_memo (Forge 47.4.16 with Oculus 1.8.0, client).
 *
 * getRenderLayers runs for every block of every chunk mesh. The whole method is wrapped (MixinExtras @WrapMethod, so
 * Oculus's lookup at its start and Embeddium's leaves redirect inside it stay in place and run whenever the original
 * runs): a block whose answer is remembered with unchanged inputs gets that answer, everything else calls the original
 * (see RenderLayerMemo). setRenderLayer, the only writer of Forge's map, advances the memo version at its start and at
 * its return. No Forge or Oculus code is carried.
 */
@Mixin(value = ItemBlockRenderTypes.class, remap = false)
public abstract class RenderLayerMemoMixin {
    @WrapMethod(method = "getRenderLayers")
    private static ChunkRenderTypeSet bons$rememberedLayers(BlockState state, Operation<ChunkRenderTypeSet> original) {
        return RenderLayerMemo.layers(state, original);
    }

    @Inject(method = "setRenderLayer(Lnet/minecraft/world/level/block/Block;Lnet/minecraftforge/client/ChunkRenderTypeSet;)V",
            at = {@At("HEAD"), @At("RETURN")})
    private static void bons$layersChanging(Block block, ChunkRenderTypeSet layers, CallbackInfo ci) {
        RenderLayerMemo.layersChanging();
    }
}
