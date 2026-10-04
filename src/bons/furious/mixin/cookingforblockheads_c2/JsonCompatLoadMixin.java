package bons.furious.mixin.cookingforblockheads_c2;

import bons.furious.patch.cookingforblockheads_c2.CompatReloadOnce;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.Optional;
import net.blay09.mods.cookingforblockheads.compat.json.JsonCompatLoader;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * cookingforblockheads_compat_reload_once (Cooking for Blockheads 16.0.15, both sides): JsonCompatLoader.
 *
 * The reload listener's whole load (onResourceManagerReload, m_6213_) runs inside CompatReloadOnce's scope, so the registry
 * adds it makes can tell a repeat of an earlier json load from a first one. The non-food list is the one collection this
 * class fills itself (findItemStack(...).ifPresent(nonFoodRecipes::add) in the foods loop, the first findItemStack call of
 * load): a repeat comes back as an empty Optional there. All Rights Reserved target: wraps only, no code carried.
 */
@Mixin(value = JsonCompatLoader.class, remap = false)
public abstract class JsonCompatLoadMixin {
    @WrapMethod(method = "m_6213_")
    private void bons$oneLoad(ResourceManager resourceManager, Operation<Void> original) {
        CompatReloadOnce.load(original, resourceManager);
    }

    @ModifyExpressionValue(method = "load", at = @At(value = "INVOKE", ordinal = 0,
            target = "Lnet/blay09/mods/cookingforblockheads/compat/json/JsonCompatLoader;findItemStack(Lnet/minecraft/resources/ResourceLocation;)Ljava/util/Optional;"))
    private static Optional<ItemStack> bons$nonFood(Optional<ItemStack> found) {
        return CompatReloadOnce.nonFood(JsonCompatLoader.nonFoodRecipes, found);
    }
}
