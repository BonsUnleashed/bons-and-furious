package bons.furious.mixin.create_logistics;

import net.minecraft.world.CompoundContainer;
import net.minecraft.world.Container;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * create_item_helper_empty_slots / create_single_pass_extraction (Minecraft 1.20.1, both sides): read-only access to the
 * two halves of a double chest's CompoundContainer, so PureHandlers can check which classes answer its reads.
 */
@Mixin(value = CompoundContainer.class, remap = false)
public interface CompoundContainerAccessor {
    @Accessor("f_18910_")
    Container bons$first();

    @Accessor("f_18911_")
    Container bons$second();
}
