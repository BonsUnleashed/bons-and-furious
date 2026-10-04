package bons.furious.mixin.create_logistics;

import com.simibubi.create.content.logistics.filter.FilterItemStack;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringBehaviour;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * create_single_pass_extraction (Create 6.0.8, both sides): read-only access to a FilteringBehaviour's filter, so the
 * switch can tell a plain item filter (FilterItemStack itself) from list, attribute and address filters. Changes nothing.
 */
@Mixin(value = FilteringBehaviour.class, remap = false)
public interface FilteringBehaviourAccessor {
    @Accessor("filter")
    FilterItemStack bons$filter();
}
