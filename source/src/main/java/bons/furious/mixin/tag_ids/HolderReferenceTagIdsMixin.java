package bons.furious.mixin.tag_ids;

import bons.furious.patch.tag_ids.TagIdCarrier;
import bons.furious.patch.tag_ids.TagIds;
import java.util.Set;
import net.minecraft.core.Holder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * vanilla_tag_membership_ids (Minecraft 1.21.1 with NeoForge 21.1.252; both sides).
 *
 * Holder.Reference.is(TagKey) is "tags.contains(key)" on the holder's immutable tag set. The contains call goes through
 * {@link #bons$member}: the holder keeps the sorted numbers of that set object (TagIds.IdSet, rebuilt whenever the holder's
 * set is another object, i.e. after every bindTags) and answers whether the key's number is among them; equal keys have
 * equal numbers, so the answer is contains()'s. Sets that are not the JDK's immutable sets, null and non-TagKey arguments,
 * and the runtime switch off make the original call. A plain @Redirect: the method is among the hottest in the game and a
 * WrapOperation's original call would allocate. No mixin of the pinned 1.21.1 target jars targets this method. No
 * Minecraft code is carried.
 *
 * Ported to 1.21.1: the selector names the full descriptor, is(Lnet/minecraft/tags/TagKey;)Z. With Mojang names the
 * class has five methods called "is" (ResourceLocation, ResourceKey, TagKey, Holder, Predicate); the 1.20.1 SRG name
 * m_203656_ was unique. The method body is unchanged (one Set.contains on the tags field).
 */
@Mixin(value = Holder.Reference.class, remap = false)
public abstract class HolderReferenceTagIdsMixin {
    /** The numbers of the tag set they were built from (TagIds.IdSet.source); null until the first tag test. */
    @Unique
    private TagIds.IdSet bons$tagIds;

    @Redirect(method = "is(Lnet/minecraft/tags/TagKey;)Z", at = @At(value = "INVOKE", target = "Ljava/util/Set;contains(Ljava/lang/Object;)Z"))
    private boolean bons$member(Set<?> tags, Object key) {
        if (key instanceof TagIdCarrier carrier && TagIds.enabled) {
            TagIds.IdSet ids = this.bons$tagIds;
            if (ids == null || ids.source != tags) {
                ids = TagIds.build(tags);
                if (ids == null) return tags.contains(key);
                this.bons$tagIds = ids;
            }
            boolean answer = ids.has(TagIds.idOf(carrier));
            return TagIds.SHADOW ? TagIds.shadow(tags, key, answer) : answer;
        }
        return tags.contains(key);
    }
}
