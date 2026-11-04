package bons.furious.mixin.bettercombat_c49;

import bons.furious.patch.bettercombat_c49.WeaponMemo;
import bons.furious.patch.bettercombat_c49.WeaponMemoHolder;
import net.minecraft.world.item.Item;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * bettercombat_weapon_attributes_memo (Better Combat 1.8.6+1.20.1): adds one @Unique reference field to Item, the slot of
 * WeaponMemo's per-item record, with a getter and a setter (interface WeaponMemoHolder). No method of Item is touched. Applied
 * only together with the switch's WeaponRegistry mixin (same key, same guards).
 */
@Mixin(value = Item.class, remap = false)
public abstract class ItemWeaponMemoMixin implements WeaponMemoHolder {
    @Unique
    private WeaponMemo.Memo bons$bcWeaponMemo;

    @Override
    public WeaponMemo.Memo bons$bcMemo() {
        return this.bons$bcWeaponMemo;
    }

    @Override
    public void bons$bcSetMemo(WeaponMemo.Memo memo) {
        this.bons$bcWeaponMemo = memo;
    }
}
