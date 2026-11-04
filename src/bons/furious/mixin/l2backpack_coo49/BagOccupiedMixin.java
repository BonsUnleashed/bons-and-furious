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
 * l2backpack_bag_count_from_nbt (L2 Backpack 2.6.0, both sides; exact variant): a cancellable HEAD inject on
 * AbstractBag.getOccupied that answers with BagCount.count (the used slots counted from the stored list with the rules an
 * ItemStack made from each entry follows) after the bag's own getInvSize; a bag class with its own getContent, or an
 * out-of-range size, runs the shipped method. Our own logic only (LGPL-2.1 target; none of its code is carried).
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
