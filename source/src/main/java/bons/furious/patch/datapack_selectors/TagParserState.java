package bons.furious.patch.datapack_selectors;

import java.util.function.Predicate;

/**
 * vanilla_selector_tag_index (Minecraft 1.21.1 with NeoForge 21.1.252, both sides): state an EntitySelectorParser carries
 * while it parses one selector (implemented by bons.furious.mixin.datapack_selectors.EntitySelectorParserTagMixin).
 * Carries no Minecraft code.
 *
 * Ported to 1.21.1: the parser keeps its predicates in a list (1.20.1 chained one Predicate field), so the handlers name
 * the predicate object they are about to add; the parser state follows that object into the list by identity.
 */
public interface TagParserState {
    /** The tag option handler's values while it runs: its negation flag and its tag string (null until read). */
    void bons$noteTagOption(boolean negated, String tag);

    boolean bons$tagNegated();

    /**
     * Called by the tag option handler just before it adds its predicate (this object): that predicate is a pure one, and
     * the noted tag is kept, once exactly this predicate has been added, when the tag is positive and not empty and every
     * predicate in the parser's list so far is pure (the "@e"/"@n" Entity::isAlive, then type= or tag= predicates).
     */
    void bons$offerTag(Predicate<?> predicate);

    /** Called by the type option handler just before it adds its predicate (this object): that predicate is a pure one. */
    void bons$markPure(Predicate<?> predicate);
}
