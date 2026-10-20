package bons.furious.mixin.itemfilters;

import bons.furious.patch.itemfilters.VerifiedKeyHolder;
import dev.latvian.mods.itemfilters.item.TagFilterItem;
import net.minecraft.tags.TagKey;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * itemfilters_tag_key_reuse (Item Filters 2001.1.0-build.53, both sides): one field on TagFilterItem.TagData holding the
 * value TagKeyReuse has verified (the interner's instance, vanilla-valid location). Written and read only by TagKeyReuse; a
 * reference field, so a racing reader sees either the old or the new key and at worst verifies again.
 */
@Mixin(value = TagFilterItem.TagData.class, remap = false)
public abstract class TagDataVerifiedKeyMixin implements VerifiedKeyHolder {
    @Unique
    private TagKey<?> bons$verifiedKeyField;

    @Override
    public TagKey<?> bons$verifiedTagKey() {
        return bons$verifiedKeyField;
    }

    @Override
    public void bons$setVerifiedTagKey(TagKey<?> key) {
        bons$verifiedKeyField = key;
    }
}
