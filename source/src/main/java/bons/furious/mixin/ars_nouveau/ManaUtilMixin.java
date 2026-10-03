package bons.furious.mixin.ars_nouveau;

import com.hollingsworth.arsnouveau.api.mana.IManaDiscountEquipment;
import com.hollingsworth.arsnouveau.api.spell.Spell;
import com.hollingsworth.arsnouveau.api.util.CuriosUtil;
import com.hollingsworth.arsnouveau.api.util.ManaUtil;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

/**
 * ars_primitive_mana_discounts (Ars Nouveau 4.12.7).
 *
 * getPlayerDiscounts, which runs for every spell cost calculation, summed the discounts of worn curios, armour and
 * the casting item into an AtomicInteger through a capturing lambda passed to LazyOptional.ifPresent. The sum is now
 * a plain int and the curios handler is read with orElse(null), as CuriosUtil.hasItem does. The same items are asked
 * for their discount in the same order (curio slots, armour slots, casting item) and the int arithmetic is the same.
 */
@Mixin(value = ManaUtil.class, remap = false)
public abstract class ManaUtilMixin {
    /**
     * @author BonsUnleashed
     * @reason Sum the mana discounts in a local int instead of an AtomicInteger captured by a lambda.
     */
    @Overwrite
    public static int getPlayerDiscounts(LivingEntity e, Spell spell, ItemStack casterStack) {
        if (e == null) {
            return 0;
        }
        int discounts = 0;
        IItemHandlerModifiable items = CuriosUtil.getAllWornItems(e);
        if (items != null) {
            for (int i = 0; i < items.getSlots(); i++) {
                ItemStack item = items.getStackInSlot(i);
                Item worn = item.getItem();
                if (worn instanceof IManaDiscountEquipment) {
                    IManaDiscountEquipment discountItem = (IManaDiscountEquipment) worn;
                    discounts += discountItem.getManaDiscount(item, spell);
                }
            }
        }
        for (ItemStack armor : e.getArmorSlots()) {
            Item worn = armor.getItem();
            if (worn instanceof IManaDiscountEquipment) {
                IManaDiscountEquipment discountItem = (IManaDiscountEquipment) worn;
                discounts += discountItem.getManaDiscount(armor, spell);
            }
        }
        Item held = casterStack.getItem();
        if (held instanceof IManaDiscountEquipment) {
            IManaDiscountEquipment discountEquipment = (IManaDiscountEquipment) held;
            discounts += discountEquipment.getManaDiscount(casterStack, spell);
        }
        return discounts;
    }
}
