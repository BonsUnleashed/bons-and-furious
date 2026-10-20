package bons.furious.mixin.itemfilters;

import bons.furious.patch.itemfilters.TagKeyReuse;
import dev.latvian.mods.itemfilters.item.TagFilterItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * itemfilters_tag_key_reuse (Item Filters 2001.1.0-build.53, both sides): cancellable HEAD injections into
 * TagFilterItem.filter and filterItem that answer with the TagKey the filter's data already holds (TagKeyReuse explains why
 * the answer is stock's). Our own logic only. The handlers stay a few bytes long (TagKeyReuse answers 0 = run stock,
 * 1 = true, 2 = false) so HotSpot inlines them and removes the CallbackInfoReturnable: measured 24 B per filterItem call when
 * the handler held the logic. Stock's body runs whenever the helper says 0: switch off, a null argument (stock throws its own
 * exception with its own message), an empty stack or AIR (stock answers false without reading the filter), or a filter value
 * the kept key cannot stand for.
 */
@Mixin(value = TagFilterItem.class, remap = false)
public abstract class TagFilterItemMixin {
    @Inject(method = "filter(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;)Z", at = @At("HEAD"), cancellable = true)
    private void bons$ifFilter(ItemStack filter, ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        int a = TagKeyReuse.filter(this, filter, stack);
        if (a != 0) cir.setReturnValue(a == 1);
    }

    @Inject(method = "filterItem(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/Item;)Z", at = @At("HEAD"), cancellable = true)
    private void bons$ifFilterItem(ItemStack filter, Item item, CallbackInfoReturnable<Boolean> cir) {
        int a = TagKeyReuse.filterItem(this, filter, item);
        if (a != 0) cir.setReturnValue(a == 1);
    }
}
