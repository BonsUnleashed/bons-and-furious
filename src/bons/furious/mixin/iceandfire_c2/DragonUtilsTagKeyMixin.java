package bons.furious.mixin.iceandfire_c2;

import bons.furious.patch.iceandfire_c2.EntityTagKeys;
import com.github.alexthe666.iceandfire.entity.util.DragonUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraftforge.registries.tags.ITagManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * iceandfire_entity_tag_keys (Ice and Fire 2.1.13-1.20.1-beta-5, LGPL; both sides), part 2 of 2.
 *
 * DragonUtils.isVillager and DragonUtils.isDragonTargetable (dragon targeting) create and intern a new TagKey on every
 * call. The redirects hand back the key created for that location the first time (EntityTagKeys); the methods are
 * otherwise unchanged (isVillager still returns false when the registry has no tag manager, before the key).
 */
@Mixin(value = DragonUtils.class, remap = false)
public abstract class DragonUtilsTagKeyMixin {
    @SuppressWarnings("rawtypes")
    @Redirect(method = {"isVillager", "isDragonTargetable"}, at = @At(value = "INVOKE",
            target = "Lnet/minecraftforge/registries/tags/ITagManager;createTagKey(Lnet/minecraft/resources/ResourceLocation;)Lnet/minecraft/tags/TagKey;"))
    private static TagKey bons$keptTagKey(ITagManager tags, ResourceLocation location) {
        return EntityTagKeys.key(tags, location);
    }
}
