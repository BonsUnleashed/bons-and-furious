package bons.furious.mixin.create_logistics;

import net.minecraft.world.WorldlyContainer;
import net.minecraftforge.items.wrapper.SidedInvWrapper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * create_item_helper_empty_slots / create_single_pass_extraction (Forge 47.4.16, both sides): read-only access to the
 * container behind a SidedInvWrapper, so PureHandlers can check which class answers its reads. Changes nothing.
 */
@Mixin(value = SidedInvWrapper.class, remap = false)
public interface SidedInvWrapperAccessor {
    @Accessor("inv")
    WorldlyContainer bons$inv();
}
