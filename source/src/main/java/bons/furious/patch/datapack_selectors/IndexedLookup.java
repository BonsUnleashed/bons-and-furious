package bons.furious.patch.datapack_selectors;

import net.minecraft.util.AbortableIterationConsumer;

/**
 * vanilla_selector_type_index (Minecraft 1.21.1 with NeoForge 21.1.252, both sides): an EntityLookup with a TypeIndex
 * (implemented by bons.furious.mixin.datapack_selectors.EntityLookupMixin). Used by the shadow verification only; scans
 * reach the index through EntityLookup.getEntities. Carries no Minecraft code.
 *
 * Ported to 1.21.1: unchanged.
 */
public interface IndexedLookup {
    /** Builds or refreshes the index and hands the consumer what a scan with this filter visits, in order. */
    void bons$visitIndexed(SelectorPrefilter.TypeFirstOption filter, AbortableIterationConsumer<Object> consumer);
}
