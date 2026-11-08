package bons.furious.mixin.l2backpack_coo49;

import bons.furious.patch.l2backpack_coo49.BagCount;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * l2backpack_bag_count_from_nbt (L2 Backpack, LGPL-2.1; 1.21.1 tested build 3.1.5; both sides): a cancellable HEAD inject
 * on AbstractBag.getOccupied that answers with BagCount.count (the non-empty stacks of the bag's stored contents, read in
 * place) after the bag's own getInvSize; a bag class with its own getContent, or stored contents with more slots than the
 * bag, runs the shipped method. Our own logic only (none of L2 Backpack's code is carried).
 *
 * Ported to 1.21.1: the bag's items are an ItemContainerContents data component (bag_content), not an NBT list, so the
 * count reads the stored stacks instead of parsing entries; nothing is built or copied.
 */
@Mixin(targets = "dev.xkmc.l2backpack.content.bag.AbstractBag", remap = false)
public abstract class BagOccupiedMixin {
    @Shadow
    public abstract int getInvSize(ItemStack stack);

    @Shadow
    public abstract NonNullList<ItemStack> getContent(ItemStack stack);

    @Inject(method = "getOccupied(Lnet/minecraft/world/item/ItemStack;)I", at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    private void bons$l2backpack_coo49Occupied(ItemStack stack, CallbackInfoReturnable<Integer> cir) {
        if (!BagCount.enabled || !BagCount.plain(this.getClass())) return;
        int used = BagCount.count(this.getInvSize(stack), stack);
        if (used == BagCount.UNKNOWN) return;
        if (BagCount.SHADOW) used = BagCount.shadow(used, this.getContent(stack));
        cir.setReturnValue(used);
    }
}
