package bons.furious.mixin.transmog_coo49;

import bons.furious.patch.transmog_coo49.TransmogAppearances;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * transmog_appearance_cache_identity (Transmog forge-1.3.0+1.20, MIT; physical client; trade-off, off by default): cancellable
 * @Inject in TransmogUtils.getAppearanceItemStack(ItemStack, boolean) at its read of the TRANSMOG_ITEM_CACHE map, so after
 * the mod's own test (an untransmogged or empty stack is answered by the mod, untouched), with the stored appearance
 * compound (@Local). On a client thread the answer comes from TransmogAppearances' tables keyed by that compound object,
 * then by its hash (the same hidden-item rule); on the logical server thread, or with the switch off, the method runs on
 * as shipped. Nothing of the mod is carried (it is MIT; the tables are ours).
 */
@Mixin(targets = "com.hidoni.transmog.TransmogUtils", remap = false)
public abstract class TransmogAppearanceCacheMixin {
    @Inject(method = "getAppearanceItemStack(Lnet/minecraft/world/item/ItemStack;Z)Lnet/minecraft/world/item/ItemStack;",
            at = @At(value = "FIELD", target = "Lcom/hidoni/transmog/TransmogUtils;TRANSMOG_ITEM_CACHE:Ljava/util/Map;", opcode = Opcodes.GETSTATIC),
            cancellable = true, require = 1, allow = 1)
    private static void bons$transmogCoo49Appearance(ItemStack stack, boolean keepHidden, CallbackInfoReturnable<ItemStack> cir,
                                                     @Local(ordinal = 0) CompoundTag tag) {
        ItemStack answer = TransmogAppearances.appearance(tag, keepHidden);
        if (answer != null) cir.setReturnValue(answer);
    }
}
