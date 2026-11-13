package bons.furious.mixin.valkyrien_block_registration;

import net.minecraft.world.level.block.state.BlockBehaviour;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * valkyrien_block_registration_lazy_cache: a block's properties object (BlockBehaviour.properties, f_60439_), which holds
 * the two flags calculateSolid reads first.
 */
@Mixin(value = BlockBehaviour.class, remap = false)
public interface BlockBehaviourPropertiesAccessor {
    @Accessor(value = "f_60439_", remap = false)
    BlockBehaviour.Properties bons$properties();
}
