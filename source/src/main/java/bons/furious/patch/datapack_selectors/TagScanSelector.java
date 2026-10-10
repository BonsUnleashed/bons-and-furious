package bons.furious.patch.datapack_selectors;

/**
 * vanilla_selector_tag_index (Minecraft 1.21.1 with NeoForge 21.1.252, both sides): what an EntitySelector carries for
 * tag scans (implemented by bons.furious.mixin.datapack_selectors.EntitySelectorTagScanMixin): the usable tags its parse
 * found (null: none) and the last TagScanTest built around its scan's type test (reused while that test is the same
 * object). Carries no Minecraft code.
 *
 * Ported to 1.21.1: unchanged.
 */
public interface TagScanSelector {
    void bons$setScanTags(String[] tags);

    String[] bons$scanTags();

    TagIndex.TagScanTest bons$tagTest();

    void bons$setTagTest(TagIndex.TagScanTest test);
}
