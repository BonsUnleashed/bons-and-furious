package bons.furious.mixin.celestialcore_match_c49;

import bons.furious.patch.celestialcore_match_c49.RecipeBlockMatch;
import com.bawnorton.mixinsquared.TargetHandler;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.Optional;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * celestialcore_transformation_recipe_match (Celestial Core 1.6.1 and 1.5.8, both sides; the handler acts on the server),
 * part 1 of 3: a MixinSquared @TargetHandler @WrapOperation of the RecipeManager.getRecipeFor call in Celestial Core's
 * ItemEntityMixin.celestial_core$tick$recipe (merged into ItemEntity). The call runs as shipped, with RecipeBlockMatch's
 * scope set to this item, so the transformation recipes' matches() (parts 2 and 3) answer whether their block is under the
 * item. Our own logic only (Celestial Core is LGPL-2.1; none of its code is carried). Priority 1500: after Celestial Core's
 * mixin, whose handler must be merged before it can be targeted.
 */
@Mixin(value = ItemEntity.class, priority = 1500, remap = false)
public abstract class CelestialRecipeLookupMixin {
    @TargetHandler(mixin = "com.xiaoyue.celestial_core.mixin.ItemEntityMixin", name = "celestial_core$tick$recipe")
    @WrapOperation(method = "@MixinSquared:Handler", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/crafting/RecipeManager;m_44015_(Lnet/minecraft/world/item/crafting/RecipeType;Lnet/minecraft/world/Container;Lnet/minecraft/world/level/Level;)Ljava/util/Optional;"),
            require = 0, expect = 0, allow = 1)
    @SuppressWarnings({"rawtypes"})
    private Optional bons$celestialcore_match_c49Lookup(RecipeManager manager, RecipeType type, Container container, Level level, Operation<Optional> original) {
        return RecipeBlockMatch.lookup((ItemEntity) (Object) this, manager, type, container, level, original);
    }
}
