package bons.furious.patch.bettercombat_c49;

/**
 * bettercombat_weapon_attributes_memo: the per-Item slot of {@link WeaponMemo} (implemented on net.minecraft.world.item.Item
 * by bons.furious.mixin.bettercombat_c49.ItemWeaponMemoMixin; one reference field, no behaviour of Item changes).
 */
public interface WeaponMemoHolder {
    WeaponMemo.Memo bons$bcMemo();

    void bons$bcSetMemo(WeaponMemo.Memo memo);
}
