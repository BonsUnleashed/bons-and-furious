package bons.furious.mixin.elysium_sound_memo;

import java.util.Set;
import net.minecraft.core.Holder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * elysium_sound_transformer_memo (Minecraft 1.20.1, both sides, since 1.0.49): read access to Holder.Reference.tags
 * (f_205749_), the immutable set Holder.Reference.bindTags (m_205769_) replaces on every tag reload. ElysiumSoundMemo keys a
 * block's remembered sound transformer on this object. Adds no behaviour and carries no Minecraft code.
 */
@Mixin(value = Holder.Reference.class, remap = false)
public interface HolderReferenceTagsAccessor {
    @Accessor(value = "f_205749_", remap = false)
    Set<?> bons$elyTags();
}
