package bons.furious.mixin.curios_presence;

import bons.furious.patch.curios_presence.CuriosFirstMatchWrapperDuck;
import bons.furious.patch.curios_presence.CuriosPredicateLookups;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import top.theillusivec4.curios.api.SlotResult;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;
import top.theillusivec4.curios.common.capability.CurioInventoryCapability;

/**
 * curios_first_match_lean (Curios API 5.14.1+1.20.1, LGPL-3.0; both sides; since 1.0.49).
 *
 * CurioInventoryWrapper.findFirstCurio(Predicate) and findCurios(Predicate) are wrapped as a whole (MixinExtras
 * @WrapMethod): for Curios' own wrapper class CuriosPredicateLookups runs the lean twin of the walk over this wrapper's own
 * slot map (CuriosLean: the caller's predicate on the same stacks in the same order, the same records on a hit),
 * otherwise the original runs. Also adds read access to the slot map and wearer for CuriosHelper's predicate lookups.
 */
@Mixin(value = CurioInventoryCapability.CurioInventoryWrapper.class, remap = false)
public abstract class CurioInventoryWrapperFirstMatchMixin implements CuriosFirstMatchWrapperDuck {
    @Shadow
    Map<String, ICurioStacksHandler> curios;

    @Shadow
    LivingEntity wearer;

    @Override
    public Map<String, ICurioStacksHandler> bons$firstMatchCurios() {
        return this.curios;
    }

    @Override
    public LivingEntity bons$firstMatchWearer() {
        return this.wearer;
    }

    @WrapMethod(method = "findFirstCurio(Ljava/util/function/Predicate;)Ljava/util/Optional;")
    private Optional<SlotResult> bons$firstMatch(Predicate<ItemStack> filter, Operation<Optional<SlotResult>> original) {
        return CuriosPredicateLookups.wrapperFirst(this, this.curios, this.wearer, filter, original);
    }

    @WrapMethod(method = "findCurios(Ljava/util/function/Predicate;)Ljava/util/List;")
    private List<SlotResult> bons$allMatch(Predicate<ItemStack> filter, Operation<List<SlotResult>> original) {
        return CuriosPredicateLookups.wrapperAll(this, this.curios, this.wearer, filter, original);
    }
}
