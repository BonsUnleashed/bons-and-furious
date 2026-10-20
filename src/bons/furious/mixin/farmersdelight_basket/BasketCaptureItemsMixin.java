package bons.furious.mixin.farmersdelight_basket;

import bons.furious.patch.farmersdelight_basket.BasketQuery;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.List;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import vectorwing.farmersdelight.common.block.entity.Basket;
import vectorwing.farmersdelight.common.block.entity.BasketBlockEntity;

/**
 * farmersdelight_basket_query_direct (Farmer's Delight 1.20.1-1.3.4; server side; since 1.0.45): wraps
 * BasketBlockEntity.getCaptureItems (the item search every idle basket runs every tick). BasketQuery makes the same
 * getEntitiesOfClass call per box of the facing's collection shape, in the same order, without the box list, stream, flat
 * map and collector; the result list has the same items in the same order. Our own logic only.
 */
@Mixin(value = BasketBlockEntity.class, remap = false)
public abstract class BasketCaptureItemsMixin {
    @WrapMethod(method = "getCaptureItems(Lnet/minecraft/world/level/Level;Lvectorwing/farmersdelight/common/block/entity/Basket;I)Ljava/util/List;")
    private static List<ItemEntity> bons$captureItems(Level level, Basket basket, int facingIndex, Operation<List<ItemEntity>> original) {
        return BasketQuery.captureItems(level, basket, facingIndex, original);
    }
}
