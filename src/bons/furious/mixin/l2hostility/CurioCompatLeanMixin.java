package bons.furious.mixin.l2hostility;

import bons.furious.patch.l2hostility.CurioCheckLean;
import dev.xkmc.l2hostility.compat.curios.CurioCompat;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import top.theillusivec4.curios.api.SlotResult;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;

/**
 * l2hostility_curio_check_lean (L2 Hostility 2.5.19 with Curios API 5.14.1, both sides): three redirects inside
 * CurioCompat.hasItemImpl. The dropped findFirstCurio call is made only when it could be observed; the per-slot-type
 * new ResourceLocation("curios", id) and ItemTags.create(...) are answered from curios_tag_predicate_keys' kept keys
 * (CurioCheckLean explains why answers, exceptions and the remaining calls are unchanged). Our own logic only (L2 Hostility's
 * mods.toml says All rights reserved): no L2 Hostility code is carried.
 */
@Mixin(value = CurioCompat.class, remap = false)
public abstract class CurioCompatLeanMixin {
    @Redirect(method = "hasItemImpl", at = @At(value = "INVOKE", target = "Ltop/theillusivec4/curios/api/type/capability/ICuriosItemHandler;findFirstCurio(Lnet/minecraft/world/item/Item;)Ljava/util/Optional;"))
    private static Optional<SlotResult> bons$l2hFindFirst(ICuriosItemHandler handler, Item item) {
        return CurioCheckLean.findFirst(handler, item);
    }

    @Redirect(method = "hasItemImpl", at = @At(value = "NEW", target = "(Ljava/lang/String;Ljava/lang/String;)Lnet/minecraft/resources/ResourceLocation;"))
    private static ResourceLocation bons$l2hSlotLocation(String namespace, String path) {
        return CurioCheckLean.location(namespace, path);
    }

    @Redirect(method = "hasItemImpl", at = @At(value = "INVOKE", target = "Lnet/minecraft/tags/ItemTags;create(Lnet/minecraft/resources/ResourceLocation;)Lnet/minecraft/tags/TagKey;"))
    private static TagKey<Item> bons$l2hSlotTag(ResourceLocation location) {
        return CurioCheckLean.tag(location);
    }
}
