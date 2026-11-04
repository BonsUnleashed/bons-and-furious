package bons.furious.mixin.extrahnn_coo49;

import bons.furious.patch.extrahnn_coo49.ModelRenderCap;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.shadowsoffire.hostilenetworks.data.DataModel;
import net.lmor.extrahnn.client.ExtraDataModelItemStackRenderer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * extrahnn_rendered_model_cap (Extra Hostile Neural Networks 1.2.2, client; kind change, off by default): a MixinExtras
 * @WrapOperation of the one renderEntityInInventory(..., count) call in ExtraDataModelItemStackRenderer.renderByItem
 * (m_108829_): the call runs only while ModelRenderCap.draws(count) (count numbers the bound models from 0), so an item with
 * more bound models than the value draws only the first ones; the rest of the method runs as shipped. Our own logic only
 * (MIT target; none of its code is carried).
 */
@Mixin(targets = "net.lmor.extrahnn.client.ExtraDataModelItemStackRenderer", remap = false)
public abstract class ExtraModelRenderCapMixin {
    @WrapOperation(method = "m_108829_(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V",
            at = @At(value = "INVOKE", target = "Lnet/lmor/extrahnn/client/ExtraDataModelItemStackRenderer;renderEntityInInventory(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/item/ItemDisplayContext;Lnet/minecraft/world/entity/LivingEntity;Ldev/shadowsoffire/hostilenetworks/data/DataModel;I)V"),
            require = 1, allow = 1)
    private void bons$extrahnn_coo49Cap(ExtraDataModelItemStackRenderer self, PoseStack pose, ItemDisplayContext type, LivingEntity entity, DataModel model, int count,
                                        Operation<Void> original) {
        if (ModelRenderCap.draws(count)) original.call(self, pose, type, entity, model, count);
    }
}
