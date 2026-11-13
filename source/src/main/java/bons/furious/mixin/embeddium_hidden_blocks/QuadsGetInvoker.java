package bons.furious.mixin.embeddium_hidden_blocks;

import com.supermartijn642.fusion.model.types.base.BaseBakedModel;
import java.util.List;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * fusion_hidden_block_models: BaseBakedModel.Quads.get(side), the list of a model's quads for one side (null = the
 * quads that belong to no side).
 */
@Mixin(value = BaseBakedModel.Quads.class, remap = false)
public interface QuadsGetInvoker {
    @Invoker(value = "get", remap = false)
    List<?> bons$get(Direction side);
}
