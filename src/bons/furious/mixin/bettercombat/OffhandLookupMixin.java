package bons.furious.mixin.bettercombat;

import com.bawnorton.mixinsquared.TargetHandler;
import net.bettercombat.api.WeaponAttributes;
import net.bettercombat.logic.WeaponRegistry;
import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * bettercombat_offhand_lookup (Better Combat 1.8.6+1.20.1-forge).
 *
 * Better Combat's PlayerEntityMixin hooks the head of Player.getItemBySlot. On every call, for every slot, it reads both
 * held stacks and looks up their weapon attributes (registry key, map lookup, a JSON parse for items with NBT), and only
 * then checks whether the slot is the offhand, the only slot it ever changes. 1.0.19 returned from the hook at once for
 * the other slots. The three redirects below give the same result: for any other slot the two stack reads answer EMPTY
 * and the two lookups answer null without calling the inventory, other mods' inventory hooks or the weapon registry, so
 * the hook ends without touching the result. Offhand reads run unchanged. Redirects allocate nothing on this very hot
 * path, where an early cancel (a CallbackInfo) or a method wrapper (an Operation) would allocate on every equipment read.
 */
@Mixin(value = Player.class, priority = 1500, remap = false)
public abstract class OffhandLookupMixin {
    /** The main-hand stack the hook inspects. */
    @TargetHandler(mixin = "net.bettercombat.mixin.PlayerEntityMixin", name = "getEquippedStack_Pre")
    @Redirect(method = "@MixinSquared:Handler",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Inventory;m_36056_()Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack bons$selectedStack(Inventory inventory, EquipmentSlot slot, CallbackInfoReturnable<ItemStack> cir) {
        return slot == EquipmentSlot.OFFHAND ? inventory.m_36056_() : ItemStack.f_41583_;
    }

    /** The offhand stack the hook inspects. */
    @TargetHandler(mixin = "net.bettercombat.mixin.PlayerEntityMixin", name = "getEquippedStack_Pre")
    @Redirect(method = "@MixinSquared:Handler",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/core/NonNullList;get(I)Ljava/lang/Object;"))
    private Object bons$offhandStack(NonNullList<ItemStack> offhand, int index, EquipmentSlot slot, CallbackInfoReturnable<ItemStack> cir) {
        return slot == EquipmentSlot.OFFHAND ? offhand.get(index) : ItemStack.f_41583_;
    }

    /** The weapon attribute lookups for both stacks. */
    @TargetHandler(mixin = "net.bettercombat.mixin.PlayerEntityMixin", name = "getEquippedStack_Pre")
    @Redirect(method = "@MixinSquared:Handler",
            at = @At(value = "INVOKE", target = "Lnet/bettercombat/logic/WeaponRegistry;getAttributes(Lnet/minecraft/world/item/ItemStack;)Lnet/bettercombat/api/WeaponAttributes;"))
    private WeaponAttributes bons$weaponAttributes(ItemStack stack, EquipmentSlot slot, CallbackInfoReturnable<ItemStack> cir) {
        return slot == EquipmentSlot.OFFHAND ? WeaponRegistry.getAttributes(stack) : null;
    }
}
