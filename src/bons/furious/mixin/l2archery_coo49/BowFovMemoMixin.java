package bons.furious.mixin.l2archery_coo49;

import bons.furious.patch.l2archery_coo49.BowFov;
import dev.xkmc.l2archery.content.item.BowData;
import dev.xkmc.l2archery.content.item.GenericBowItem;
import dev.xkmc.l2archery.content.item.IBowConfig;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * l2archery_fov_config_memo (L2 Archery 2.5.0, client; exact): two @Redirect in GenericEventHandler.fov: the
 * BowData.of(bow, stack) call (still made every frame) hands out the kept BowData when the new one holds the same content
 * under the same merged stat config, and the two data.getConfig() calls on the kept BowData hand out a fixed config with
 * the kept fov_time() and fov(); the handler's own arithmetic runs unchanged, and frames without an L2 bow in the main hand
 * never reach either call. See BowFov for why the numbers are the same. Our own logic only (LGPL-2.1 target; none of its
 * code is carried).
 */
@Mixin(targets = "dev.xkmc.l2archery.events.GenericEventHandler", remap = false)
public abstract class BowFovMemoMixin {
    @Redirect(method = "fov(Lnet/minecraftforge/client/event/ComputeFovModifierEvent;)V",
            at = @At(value = "INVOKE", target = "Ldev/xkmc/l2archery/content/item/BowData;of(Ldev/xkmc/l2archery/content/item/GenericBowItem;Lnet/minecraft/world/item/ItemStack;)Ldev/xkmc/l2archery/content/item/BowData;"),
            require = 1, allow = 1)
    private static BowData bons$l2archery_coo49FovData(GenericBowItem bow, ItemStack stack) {
        return BowFov.data(bow, stack);
    }

    @Redirect(method = "fov(Lnet/minecraftforge/client/event/ComputeFovModifierEvent;)V",
            at = @At(value = "INVOKE", target = "Ldev/xkmc/l2archery/content/item/BowData;getConfig()Ldev/xkmc/l2archery/content/item/IBowConfig;"),
            require = 1, allow = 2)
    private static IBowConfig bons$l2archery_coo49FovConfig(BowData data) {
        return BowFov.config(data);
    }
}
