package bons.furious.mixin.elysium_sound_memo;

import java.util.Set;
import net.minecraft.core.Holder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * elysium_sound_transformer_memo (Minecraft 1.21.1, both sides): read access to Holder.Reference.tags, the immutable set
 * Holder.Reference.bindTags replaces on every tag reload (Set.copyOf). ElysiumSoundMemo keys a block's remembered sound
 * transformer on this object. Adds no behaviour and carries no Minecraft code.
 */
@Mixin(value = Holder.Reference.class, remap = false)
public interface HolderReferenceTagsAccessor {
    @Accessor(value = "tags", remap = false)
    Set<?> bons$elyTags();
}
