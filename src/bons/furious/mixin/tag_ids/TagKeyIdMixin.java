package bons.furious.mixin.tag_ids;

import bons.furious.patch.tag_ids.TagIdCarrier;
import net.minecraft.tags.TagKey;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * vanilla_tag_membership_ids (Minecraft 1.20.1 on Forge 47.4.16; both sides): gives every TagKey one int field, the number
 * of its content (bons.furious.patch.tag_ids.TagIds.idOf; 0 until first asked). The record's components, constructor,
 * equals, hashCode and toString are untouched (the generated methods list only the two components). No Minecraft code.
 */
@Mixin(value = TagKey.class, remap = false)
public abstract class TagKeyIdMixin implements TagIdCarrier {
    @Unique   // 1.0.34: transient: Gson reads a record's non-transient fields as components and fails without an accessor
    private transient int bons$id;

    @Override
    public int bons$tagId() {
        return this.bons$id;
    }

    @Override
    public void bons$tagId(int id) {
        this.bons$id = id;
    }
}
