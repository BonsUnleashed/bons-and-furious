package bons.furious.mixin.supplementaries_lean;

import java.util.Set;
import net.minecraft.core.Holder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * supplementaries_map_tint_memo (Minecraft 1.20.1, both sides): read access to Holder.Reference.tags (f_205749_), the
 * immutable tag set bindTags (m_205769_) replaces with a new object on every tag (re)load; null while no tags are bound.
 * Adds no behaviour and carries no Minecraft code. Lithostitched's HolderReferenceAccessor and our tag_ids mixin on the
 * same class use other member names.
 */
@Mixin(value = Holder.Reference.class, remap = false)
public interface HolderReferenceTagsAccessor {
    @Accessor(value = "f_205749_", remap = false)
    Set<?> bons$suppTagSet();
}
