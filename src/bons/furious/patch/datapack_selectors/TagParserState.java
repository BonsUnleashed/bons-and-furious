package bons.furious.patch.datapack_selectors;

/**
 * vanilla_selector_tag_index (Minecraft 1.20.1, both sides): state an EntitySelectorParser carries while it parses one
 * selector (implemented by bons.furious.mixin.datapack_selectors.EntitySelectorParserTagMixin). Carries no Minecraft code.
 */
public interface TagParserState {
    /** The tag option handler's values while it runs: its negation flag and its tag string (null until read). */
    void bons$noteTagOption(boolean negated, String tag);

    boolean bons$tagNegated();

    /**
     * Called by the tag option handler just before it adds its predicate: the noted tag is kept when it is positive and
     * not empty, the selector is "@e" and every predicate added so far came from a type= or tag= option.
     */
    void bons$offerTag();

    /** Called by the type and tag option handlers just before their addPredicate call: that predicate is a pure one. */
    void bons$markPure();
}
