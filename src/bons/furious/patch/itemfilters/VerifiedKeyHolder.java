package bons.furious.patch.itemfilters;

import net.minecraft.tags.TagKey;

/**
 * Bons and Furious switch itemfilters_tag_key_reuse (Item Filters 2001.1.0-build.53, both sides): added to Item Filters'
 * TagFilterItem.TagData by TagDataVerifiedKeyMixin. It remembers the one TagKey of that data object that TagKeyReuse has
 * checked to be the interned instance with a vanilla-valid location, so the check runs once per stored value instead of
 * once per filter test. Nothing else reads or writes it.
 */
public interface VerifiedKeyHolder {
    TagKey<?> bons$verifiedTagKey();

    void bons$setVerifiedTagKey(TagKey<?> key);
}
