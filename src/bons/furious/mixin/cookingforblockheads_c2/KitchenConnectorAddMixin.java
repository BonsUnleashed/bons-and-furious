package bons.furious.mixin.cookingforblockheads_c2;

import bons.furious.patch.cookingforblockheads_c2.CompatReloadOnce;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.Collection;
import java.util.List;
import net.blay09.mods.cookingforblockheads.KitchenMultiBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * cookingforblockheads_compat_reload_once (Cooking for Blockheads 16.0.15, both sides): KitchenMultiBlock.registerConnectorBlock,
 * which a compat json's kitchenConnectors reach on every load. A block an earlier json load already registered is not added
 * a second time (the kitchen scan checks blockConnectors.contains per neighbour); see CompatReloadOnce. All Rights Reserved
 * target: wrap only.
 */
@Mixin(value = KitchenMultiBlock.class, remap = false)
public abstract class KitchenConnectorAddMixin {
    @WrapOperation(method = "registerConnectorBlock", at = @At(value = "INVOKE", target = "Ljava/util/List;add(Ljava/lang/Object;)Z"))
    private static boolean bons$connector(List<Object> list, Object block, Operation<Boolean> original) {
        return CompatReloadOnce.listAdd("connectors", (Collection<Object>) list, block, original);
    }
}
