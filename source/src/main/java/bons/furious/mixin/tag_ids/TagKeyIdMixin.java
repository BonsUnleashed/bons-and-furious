package bons.furious.mixin.tag_ids;

import bons.furious.patch.tag_ids.TagIdCarrier;
import net.minecraft.tags.TagKey;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * vanilla_tag_membership_ids (Minecraft 1.21.1 with NeoForge 21.1.252; both sides): gives every TagKey one int field, the
 * number of its content (bons.furious.patch.tag_ids.TagIds.idOf; 0 until first asked). The record's components,
 * constructor, equals, hashCode and toString are untouched (the generated methods list only the two components). No
 * Minecraft code.
 */
@Mixin(value = TagKey.class, remap = false)
public abstract class TagKeyIdMixin implements TagIdCarrier {
    @Unique
    private int bons$id;

    @Override
    public int bons$tagId() {
        return this.bons$id;
    }

    @Override
    public void bons$tagId(int id) {
        this.bons$id = id;
    }
}
