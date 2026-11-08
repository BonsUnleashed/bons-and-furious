package bons.furious.mixin.bettercombat_c49;

import bons.furious.patch.bettercombat_c49.WeaponMemo;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.bettercombat.api.WeaponAttributes;
import net.bettercombat.logic.WeaponRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * bettercombat_weapon_attributes_memo (Better Combat; 1.21.1 tested build: Better Combat 2.4.0+1.21.1 for NeoForge; both
 * sides): MixinExtras @WrapMethod on WeaponRegistry.getAttributes(ItemStack) answers a stack without the weapon preset
 * component from the per-item record (WeaponMemo), otherwise the method itself runs; @Inject at the head and return of
 * register(ResourceLocation, WeaponAttributes) and of decodeRegistry (the two writers of the registrations map) bump the
 * generation. Injectors only.
 *
 * Ported to 1.21.1: 2.4.0's getAttributes reads the bettercombat:preset_id data component where 1.8.6 read the stack's NBT
 * tag, and its decodeRegistry(Packets.WeaponRegistrySync) clears and refills the registrations map instead of swapping in
 * a new one, so it is bumped at its head as well.
 */
@Mixin(value = WeaponRegistry.class, remap = false)
public abstract class WeaponRegistryMemoMixin {
    @WrapMethod(method = "getAttributes(Lnet/minecraft/world/item/ItemStack;)Lnet/bettercombat/api/WeaponAttributes;")
    private static WeaponAttributes bons$bcAttributes(ItemStack stack, Operation<WeaponAttributes> original) {
        return WeaponMemo.attributes(stack, original);
    }

    @Inject(method = "register(Lnet/minecraft/resources/ResourceLocation;Lnet/bettercombat/api/WeaponAttributes;)V", at = {@At("HEAD"), @At("RETURN")})
    private static void bons$bcRegistered(ResourceLocation itemId, WeaponAttributes attributes, CallbackInfo ci) {
        WeaponMemo.bump();
    }

    @Inject(method = "decodeRegistry", at = {@At("HEAD"), @At("RETURN")})
    private static void bons$bcDecoded(CallbackInfo ci) {
        WeaponMemo.bump();
    }
}
