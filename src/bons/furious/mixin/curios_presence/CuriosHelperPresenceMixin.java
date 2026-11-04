package bons.furious.mixin.curios_presence;

import bons.furious.patch.curios_presence.CuriosItemLookups;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.List;
import java.util.Optional;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.apache.commons.lang3.tuple.ImmutableTriple;
import org.spongepowered.asm.mixin.Mixin;
import top.theillusivec4.curios.api.SlotResult;
import top.theillusivec4.curios.common.CuriosHelper;

/**
 * curios_equipped_presence (Curios API 5.14.1+1.20.1, LGPL-3.0; both sides; since 1.0.49).
 *
 * Curios' CuriosHelper.findEquippedCurio(Item, entity), findFirstCurio(entity, Item) and findCurios(entity, Item) are
 * wrapped as a whole (MixinExtras @WrapMethod): for Curios' own helper class CuriosItemLookups resolves the inventory as
 * Curios does and runs the lean walk (CuriosLean), without the Item lambda, LazyOptional.map's Optional and the miss
 * triples; otherwise the original runs.
 */
@Mixin(value = CuriosHelper.class, remap = false)
public abstract class CuriosHelperPresenceMixin {
    @WrapMethod(method = "findEquippedCurio(Lnet/minecraft/world/item/Item;Lnet/minecraft/world/entity/LivingEntity;)Ljava/util/Optional;")
    private Optional<ImmutableTriple<String, Integer, ItemStack>> bons$presenceEquipped(Item item, LivingEntity entity,
                                                                                      Operation<Optional<ImmutableTriple<String, Integer, ItemStack>>> original) {
        return CuriosItemLookups.helperEquipped(this, item, entity, original);
    }

    @WrapMethod(method = "findFirstCurio(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/Item;)Ljava/util/Optional;")
    private Optional<SlotResult> bons$presenceFirst(LivingEntity entity, Item item, Operation<Optional<SlotResult>> original) {
        return CuriosItemLookups.helperFirst(this, entity, item, original);
    }

    @WrapMethod(method = "findCurios(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/Item;)Ljava/util/List;")
    private List<SlotResult> bons$presenceAll(LivingEntity entity, Item item, Operation<List<SlotResult>> original) {
        return CuriosItemLookups.helperAll(this, entity, item, original);
    }
}
