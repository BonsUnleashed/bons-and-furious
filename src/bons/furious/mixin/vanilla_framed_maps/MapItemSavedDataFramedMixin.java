package bons.furious.mixin.vanilla_framed_maps;

import bons.furious.patch.vanilla_framed_maps.FramedMaps;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * vanilla_framed_map_holder_scan (Minecraft 1.20.1 on Forge 47.4.16, server side incl. the integrated server):
 * MapItemSavedData.tickCarriedBy (m_77918_), the per-holder test `player.getInventory().contains(map) || map.isFramed()`
 * in its carriedBy loop: the contains call (ordinal 1; ordinal 0 is the first, whose answer matters) is answered true
 * without scanning for a framed map (FramedMaps; same branch either way). Moonlight's and Better Nether Map's TAIL
 * injections and Vivecraft's getYRot wrapper in the same method are untouched. require = 0: a mod that rewrites the loop
 * leaves the switch out.
 */
@Mixin(value = MapItemSavedData.class, remap = false)
public abstract class MapItemSavedDataFramedMixin {
    @WrapOperation(method = "m_77918_", require = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/player/Inventory;m_36063_(Lnet/minecraft/world/item/ItemStack;)Z", ordinal = 1))
    private boolean bons$framedHolder(Inventory inventory, ItemStack mapStack, Operation<Boolean> original) {
        return FramedMaps.holderHasMap(inventory, mapStack, original);
    }
}
