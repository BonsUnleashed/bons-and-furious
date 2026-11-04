package bons.furious.mixin.bettercombat_c49;

import bons.furious.patch.bettercombat_c49.WeaponMemo;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.bettercombat.api.WeaponAttributes;
import net.bettercombat.logic.WeaponRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * bettercombat_weapon_attributes_memo (Better Combat 1.8.6+1.20.1, both sides): MixinExtras @WrapMethod on
 * WeaponRegistry.getAttributes(ItemStack) answers a stack without a tag from the per-item record (WeaponMemo), otherwise
 * the method itself runs; @Inject at the head and return of register(ResourceLocation, WeaponAttributes) and at the return
 * of decodeRegistry(FriendlyByteBuf) bump the generation (the only two writers of the registrations map). Injectors only.
 */
@Mixin(value = WeaponRegistry.class, remap = false)
public abstract class WeaponRegistryMemoMixin {
    @WrapMethod(method = "getAttributes(Lnet/minecraft/world/item/ItemStack;)Lnet/bettercombat/api/WeaponAttributes;")
    private static WeaponAttributes bons$bcAttributes(ItemStack stack, Operation<WeaponAttributes> original) {
        return WeaponMemo.attributes(stack, original);
    }

    @Inject(method = "register", at = {@At("HEAD"), @At("RETURN")})
    private static void bons$bcRegistered(ResourceLocation itemId, WeaponAttributes attributes, CallbackInfo ci) {
        WeaponMemo.bump();
    }

    @Inject(method = "decodeRegistry", at = @At("RETURN"))
    private static void bons$bcDecoded(FriendlyByteBuf buffer, CallbackInfo ci) {
        WeaponMemo.bump();
    }
}
