package bons.furious.mixin.supplementaries_lean;

import java.util.Set;
import net.minecraft.core.Holder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * supplementaries_map_tint_memo (Minecraft 1.21.1, both sides): read access to Holder.Reference.tags, the immutable tag set
 * bindTags replaces with Set.copyOf(...) on every tag (re)load (Set.of() before the first bind; never null on 1.21.1).
 * Adds no behaviour and carries no Minecraft code. elysium_sound_memo's accessor and the tag_ids mixin on the same class
 * use other member names.
 */
@Mixin(value = Holder.Reference.class, remap = false)
public interface HolderReferenceTagsAccessor {
    @Accessor(value = "tags", remap = false)
    Set<?> bons$suppTagSet();
}
