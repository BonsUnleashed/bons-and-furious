package bons.furious.mixin.vanilla_framed_maps;

import bons.furious.patch.vanilla_framed_maps.FramedMaps;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import java.util.function.Predicate;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * vanilla_framed_map_holder_scan (Minecraft 1.21.1 with NeoForge 21.1.252, server side incl. the integrated server):
 * MapItemSavedData.tickCarriedBy, the per-holder test `player.getInventory().contains(matcher) || map.isFramed()` in its
 * carriedBy loop: the contains call (ordinal 1; ordinal 0 is the first, whose answer matters) is answered true without
 * scanning for a framed map (FramedMaps; same branch either way). require = 0: a mod that rewrites the loop leaves the
 * switch out.
 *
 * Ported to 1.21.1: 1.21.1's tickCarriedBy builds one Predicate (mapMatcher: same stack, or same item and same MAP_ID
 * component) and calls Inventory.contains(Predicate) at both places instead of contains(ItemStack); the wrap targets that
 * overload (same ordinal 1) and takes the map stack from the method's own argument (@Local argsOnly).
 */
@Mixin(value = MapItemSavedData.class, remap = false)
public abstract class MapItemSavedDataFramedMixin {
    @WrapOperation(method = "tickCarriedBy", require = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/player/Inventory;contains(Ljava/util/function/Predicate;)Z", ordinal = 1))
    private boolean bons$framedHolder(Inventory inventory, Predicate<ItemStack> matcher, Operation<Boolean> original,
                                      @Local(argsOnly = true) ItemStack mapStack) {
        return FramedMaps.holderHasMap(inventory, matcher, mapStack, original);
    }
}
