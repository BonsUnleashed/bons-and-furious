package bons.furious.mixin.radium_compat;

import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * vanilla_block_entity_tick_state, part 2 of 3: the block entity's own blockState field (blockState), read without the
 * getBlockState() overrides some mods add (see BlockEntityTickStateMixin).
 */
@Mixin(value = BlockEntity.class, remap = false)
public interface BlockEntityStateAccessor {
    @Accessor(value = "blockState", remap = false)
    BlockState bons$blockStateField();
}
