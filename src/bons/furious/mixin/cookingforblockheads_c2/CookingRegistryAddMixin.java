package bons.furious.mixin.cookingforblockheads_c2;

import bons.furious.patch.cookingforblockheads_c2.CompatReloadOnce;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.Collection;
import java.util.Map;
import net.blay09.mods.cookingforblockheads.registry.CookingRegistry;
import net.minecraft.core.NonNullList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * cookingforblockheads_compat_reload_once (Cooking for Blockheads 16.0.15, both sides): the six CookingRegistry adds a compat
 * json reaches (tools, water, milk lists; oven fuel, oven recipe, toaster maps). Outside a json load, and for an entry no
 * earlier json load added, the original add runs; see CompatReloadOnce. All Rights Reserved target: wraps only.
 */
@Mixin(value = CookingRegistry.class, remap = false)
public abstract class CookingRegistryAddMixin {
    @WrapOperation(method = "addToolItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/core/NonNullList;add(Ljava/lang/Object;)Z"))
    private static boolean bons$tool(NonNullList<Object> list, Object entry, Operation<Boolean> original) {
        return CompatReloadOnce.listAdd("tools", (Collection<Object>) list, entry, original);
    }

    @WrapOperation(method = "addWaterItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/core/NonNullList;add(Ljava/lang/Object;)Z"))
    private static boolean bons$water(NonNullList<Object> list, Object entry, Operation<Boolean> original) {
        return CompatReloadOnce.listAdd("water", (Collection<Object>) list, entry, original);
    }

    @WrapOperation(method = "addMilkItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/core/NonNullList;add(Ljava/lang/Object;)Z"))
    private static boolean bons$milk(NonNullList<Object> list, Object entry, Operation<Boolean> original) {
        return CompatReloadOnce.listAdd("milk", (Collection<Object>) list, entry, original);
    }

    @WrapOperation(method = "addOvenFuel", at = @At(value = "INVOKE", target = "Ljava/util/Map;put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"))
    private static Object bons$ovenFuel(Map<Object, Object> map, Object key, Object value, Operation<Object> original) {
        return CompatReloadOnce.mapPut("ovenFuel", map, key, value, original);
    }

    @WrapOperation(method = "addSmeltingItem", at = @At(value = "INVOKE", target = "Ljava/util/Map;put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"))
    private static Object bons$ovenRecipe(Map<Object, Object> map, Object key, Object value, Operation<Object> original) {
        return CompatReloadOnce.mapPut("ovenRecipe", map, key, value, original);
    }

    @WrapOperation(method = "addToasterHandler", at = @At(value = "INVOKE", target = "Ljava/util/Map;put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"))
    private static Object bons$toaster(Map<Object, Object> map, Object key, Object value, Operation<Object> original) {
        return CompatReloadOnce.mapPut("toaster", map, key, value, original);
    }
}
