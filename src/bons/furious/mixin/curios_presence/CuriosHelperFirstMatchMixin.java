package bons.furious.mixin.curios_presence;

import bons.furious.patch.curios_presence.CuriosPredicateLookups;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.apache.commons.lang3.tuple.ImmutableTriple;
import org.spongepowered.asm.mixin.Mixin;
import top.theillusivec4.curios.api.SlotResult;
import top.theillusivec4.curios.common.CuriosHelper;

/**
 * curios_first_match_lean (Curios API 5.14.1+1.20.1, LGPL-3.0; both sides; since 1.0.49).
 *
 * Curios' CuriosHelper.findEquippedCurio(Predicate, entity), findFirstCurio(entity, Predicate) and findCurios(entity,
 * Predicate) are wrapped as a whole (MixinExtras @WrapMethod): for Curios' own helper class CuriosPredicateLookups resolves
 * the inventory as Curios does and runs the lean walk (CuriosLean); otherwise the original runs.
 */
@Mixin(value = CuriosHelper.class, remap = false)
public abstract class CuriosHelperFirstMatchMixin {
    @WrapMethod(method = "findEquippedCurio(Ljava/util/function/Predicate;Lnet/minecraft/world/entity/LivingEntity;)Ljava/util/Optional;")
    private Optional<ImmutableTriple<String, Integer, ItemStack>> bons$matchEquipped(Predicate<ItemStack> filter, LivingEntity entity,
                                                                                   Operation<Optional<ImmutableTriple<String, Integer, ItemStack>>> original) {
        return CuriosPredicateLookups.helperEquipped(this, filter, entity, original);
    }

    @WrapMethod(method = "findFirstCurio(Lnet/minecraft/world/entity/LivingEntity;Ljava/util/function/Predicate;)Ljava/util/Optional;")
    private Optional<SlotResult> bons$matchFirst(LivingEntity entity, Predicate<ItemStack> filter, Operation<Optional<SlotResult>> original) {
        return CuriosPredicateLookups.helperFirst(this, entity, filter, original);
    }

    @WrapMethod(method = "findCurios(Lnet/minecraft/world/entity/LivingEntity;Ljava/util/function/Predicate;)Ljava/util/List;")
    private List<SlotResult> bons$matchAll(LivingEntity entity, Predicate<ItemStack> filter, Operation<List<SlotResult>> original) {
        return CuriosPredicateLookups.helperAll(this, entity, filter, original);
    }
}
