package bons.furious.mixin.l2archery_coo49;

import bons.furious.patch.l2archery_coo49.BowUpgrades;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * l2archery_bow_upgrades_memo (L2 Archery 2.5.0, both sides; exact): @Redirect of the one new ResourceLocation(String) in
 * GenericBowItem.getUpgrades (the per-id parse of the stored upgrade list) to the location kept for that id string. The
 * method's other code, the getOrCreateTag call and the path of a bow without upgrades included, is the shipped code. See
 * BowUpgrades for why the answers are the same. Our own logic only (LGPL-2.1 target; none of its code is carried).
 */
@Mixin(targets = "dev.xkmc.l2archery.content.item.GenericBowItem", remap = false)
public abstract class BowUpgradesMemoMixin {
    @Redirect(method = "getUpgrades(Lnet/minecraft/world/item/ItemStack;)Ljava/util/List;",
            at = @At(value = "NEW", target = "(Ljava/lang/String;)Lnet/minecraft/resources/ResourceLocation;"), require = 1, allow = 1)
    private static ResourceLocation bons$l2archery_coo49UpgradeId(String id) {
        return BowUpgrades.id(id);
    }
}
