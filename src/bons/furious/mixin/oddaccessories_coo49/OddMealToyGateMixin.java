package bons.furious.mixin.oddaccessories_coo49;

import bons.furious.patch.oddaccessories_coo49.OddScans;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import java.util.List;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * oddaccessories_meal_toy_scan_gate (Odd Accessories 1.1.6, All Rights Reserved; server side; trade-off, off by default):
 * MixinExtras @WrapOperation on the Level.getEntitiesOfClass (m_45976_) call of CollectibleMealToyItem.onCurioTick. The
 * method's own two flags, the global particle pulse and the wearer's repath tick (read from its locals, computed before the
 * search), decide: on a pulse or repath tick the search runs as shipped, otherwise it answers an empty list. What differs:
 * between those ticks baby undead keep their target and their Weakness is not refreshed (the 20-tick effect still covers
 * the at most 9-tick gap). Our own logic only.
 */
@Mixin(targets = "net.oddaccessories.item.CollectibleMealToyItem", remap = false)
public abstract class OddMealToyGateMixin {
    @SuppressWarnings({"rawtypes"})
    @WrapOperation(method = "onCurioTick(Ltop/theillusivec4/curios/api/SlotContext;Lnet/minecraft/world/item/ItemStack;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;m_45976_(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;"),
            require = 1, allow = 1)
    private List bons$oddaccessoriesCoo49MealToy(Level level, Class<?> type, AABB box, Operation<List> original,
                                                @Local(ordinal = 0) boolean particleTick, @Local(ordinal = 1) boolean repath) {
        return OddScans.toySearch(level, type, box, original, particleTick, repath);
    }
}
