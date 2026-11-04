package bons.furious.mixin.oddaccessories_coo49;

import bons.furious.patch.oddaccessories_coo49.OddBagPool;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import java.util.List;
import java.util.stream.Stream;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * oddaccessories_bag_reward_pool_memo (Odd Accessories 1.1.6, All Rights Reserved; server side): MixinExtras @WrapOperation
 * on the Stream.toList() call that ends SuspiciousBagItem.rollReward's pool stream. OddBagPool answers the list from the
 * last build while the item registry generation and size, the config's loaded state, the DISABLEDCURIOS list identity and
 * the blacklist (the list the stream's filter captured, by identity and contents) are unchanged; otherwise the stream runs
 * as shipped. The draws on the list are untouched. Our own logic only; nothing of the mod is carried.
 */
@Mixin(targets = "net.oddaccessories.item.SuspiciousBagItem", remap = false)
public abstract class OddBagPoolMixin {
    @SuppressWarnings({"rawtypes"})
    @WrapOperation(method = "rollReward(Lnet/minecraft/util/RandomSource;)Lnet/minecraft/world/item/Item;",
            at = @At(value = "INVOKE", target = "Ljava/util/stream/Stream;toList()Ljava/util/List;"),
            require = 1, allow = 1)
    private static List bons$oddaccessoriesCoo49BagPool(Stream stream, Operation<List> original, @Local(ordinal = 0) List blacklist) {
        return OddBagPool.pool(stream, blacklist, original);
    }
}
