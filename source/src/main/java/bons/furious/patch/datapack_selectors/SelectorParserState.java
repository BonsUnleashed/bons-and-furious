package bons.furious.patch.datapack_selectors;

import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.entity.EntityTypeTest;

/**
 * vanilla_selector_type_index (Minecraft 1.21.1 with NeoForge 21.1.252, both sides): state an EntitySelectorParser
 * carries while it parses one selector (implemented by bons.furious.mixin.datapack_selectors.EntitySelectorParserMixin).
 * Carries no Minecraft code.
 *
 * Ported to 1.21.1: unchanged interface; "first" is now judged on the parser's predicate list (see the mixin).
 */
public interface SelectorParserState {
    /**
     * Called by the type option handler with a scan filter built around the predicate it is about to add. The parser
     * keeps it when that predicate is the first one added after "@e"/"@n" added Entity::isAlive, i.e. while the parser's
     * predicate list still holds only that object.
     */
    void bons$offerFirstOption(EntityTypeTest<Entity, Entity> filter);

    /** The type option handler's values while it runs: its negation flag, its tag or its single type. */
    void bons$noteTypeOption(boolean negated, TagKey<EntityType<?>> tag, EntityType<?> single);

    boolean bons$typeNegated();

    TagKey<EntityType<?>> bons$typeTag();

    EntityType<?> bons$typeSingle();
}
