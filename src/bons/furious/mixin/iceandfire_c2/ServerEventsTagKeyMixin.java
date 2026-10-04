package bons.furious.mixin.iceandfire_c2;

import bons.furious.patch.iceandfire_c2.EntityTagKeys;
import com.github.alexthe666.iceandfire.event.ServerEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraftforge.registries.tags.ITagManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * iceandfire_entity_tag_keys (Ice and Fire 2.1.13-1.20.1-beta-5, LGPL; both sides), part 1 of 2.
 *
 * ServerEvents.isInEntityTag (behind isChicken, which runs for every living entity on every server tick, and the other
 * isX entity checks) creates and interns a new TagKey on every call. The redirect hands back the key created for that
 * location the first time (EntityTagKeys); the method itself is unchanged.
 */
@Mixin(value = ServerEvents.class, remap = false)
public abstract class ServerEventsTagKeyMixin {
    @SuppressWarnings("rawtypes")
    @Redirect(method = "isInEntityTag", at = @At(value = "INVOKE",
            target = "Lnet/minecraftforge/registries/tags/ITagManager;createTagKey(Lnet/minecraft/resources/ResourceLocation;)Lnet/minecraft/tags/TagKey;"))
    private static TagKey bons$keptTagKey(ITagManager tags, ResourceLocation location) {
        return EntityTagKeys.key(tags, location);
    }
}
