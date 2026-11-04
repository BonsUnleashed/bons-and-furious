package bons.furious.mixin.mysticalagriculture_coo49;

import bons.furious.patch.mysticalagriculture_coo49.AugmentLookup;
import com.blakebr0.mysticalagriculture.api.tinkering.Augment;
import com.blakebr0.mysticalagriculture.api.util.AugmentUtils;
import java.util.List;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * mysticalagriculture_augment_lookup_lean (Mystical Agriculture 7.0.24, both sides; kind opt, default on; no Mystical
 * Agriculture code carried). AugmentUtils.getAugment and getArmorAugments are answered by AugmentLookup: the same reads and
 * registry look-ups in the same order, without building the slot keys and parsing the ids on every call, and the armour's
 * augments gathered into the one list returned. See AugmentLookup. The getAugment hook sits after the mod's own tag check
 * (at its getItem call): a stack without a tag runs the mod's code untouched.
 */
@Mixin(value = AugmentUtils.class, remap = false)
public abstract class AugmentLookupMixin {
    @Inject(method = "getAugment(Lnet/minecraft/world/item/ItemStack;I)Lcom/blakebr0/mysticalagriculture/api/tinkering/Augment;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;m_41720_()Lnet/minecraft/world/item/Item;"), cancellable = true, require = 1, allow = 1)
    private static void bons$mysticalagricultureAugment(ItemStack stack, int slot, CallbackInfoReturnable<Augment> cir) {
        if (AugmentLookup.enabled) cir.setReturnValue(AugmentLookup.augment(stack, slot));
    }

    @Inject(method = "getArmorAugments(Lnet/minecraft/world/entity/player/Player;)Ljava/util/List;", at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    private static void bons$mysticalagricultureArmorAugments(Player player, CallbackInfoReturnable<List<Augment>> cir) {
        if (AugmentLookup.enabled) cir.setReturnValue(AugmentLookup.armor(player));
    }
}
