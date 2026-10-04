package bons.furious.patch.datapack_selectors;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityTypeTest;

/**
 * vanilla_selector_type_index (Minecraft 1.21.1 with NeoForge 21.1.252, both sides): the scan filter an EntitySelector
 * carries (implemented by bons.furious.mixin.datapack_selectors.EntitySelectorMixin). Null for every selector the switch
 * does not apply to. Carries no Minecraft code.
 *
 * Ported to 1.21.1: unchanged.
 */
public interface PrefilteredSelector {
    void bons$setScanPrefilter(EntityTypeTest<Entity, Entity> filter);

    EntityTypeTest<Entity, Entity> bons$scanPrefilter();
}
