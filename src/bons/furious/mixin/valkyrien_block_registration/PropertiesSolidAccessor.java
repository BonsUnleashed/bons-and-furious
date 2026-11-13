package bons.furious.mixin.valkyrien_block_registration;

import net.minecraft.world.level.block.state.BlockBehaviour;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * valkyrien_block_registration_lazy_cache: BlockBehaviour.Properties.forceSolidOn (f_279618_) and forceSolidOff
 * (f_279665_), the two flags BlockStateBase.calculateSolid (m_280210_) reads before it looks at the collision shape.
 */
@Mixin(value = BlockBehaviour.Properties.class, remap = false)
public interface PropertiesSolidAccessor {
    @Accessor(value = "f_279618_", remap = false)
    boolean bons$forceSolidOn();

    @Accessor(value = "f_279665_", remap = false)
    boolean bons$forceSolidOff();
}
