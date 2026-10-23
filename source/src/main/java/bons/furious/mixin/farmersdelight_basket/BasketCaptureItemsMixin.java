package bons.furious.mixin.farmersdelight_basket;

import bons.furious.patch.farmersdelight_basket.BasketQuery;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.List;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import vectorwing.farmersdelight.common.block.entity.Basket;

/** Farmer's Delight 1.21.1-1.3.4: the same ordered item query is now a Basket default method. */
@Mixin(value = Basket.class, remap = false)
public interface BasketCaptureItemsMixin {
    @WrapMethod(method = "getItemsToCollect(Lnet/minecraft/world/level/Level;I)Ljava/util/List;")
    default List<ItemEntity> bons$captureItems(Level level, int facingIndex, Operation<List<ItemEntity>> original) {
        return BasketQuery.captureItems(level, (Basket) this, facingIndex, original);
    }
}
