package bons.furious.mixin.forge;

import bons.furious.patch.forge.RenderLayerMemo;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * forge_render_layer_memo (Forge 47.4.16 with Oculus 1.8.0, client): the per-block slot for the remembered render layers.
 * The slot holds one immutable entry (a record: final fields), so meshing threads always see a consistent answer and its
 * inputs without any locking.
 */
@Mixin(value = Block.class, remap = false)
public abstract class BlockRenderLayerMemoMixin implements RenderLayerMemo.Remembered {
    @Unique
    private Object bons$renderLayers;

    @Override
    public Object bons$renderLayers() {
        return this.bons$renderLayers;
    }

    @Override
    public void bons$renderLayers(Object entry) {
        this.bons$renderLayers = entry;
    }
}
