package bons.furious.mixin.vanilla_render_tradeoffs;

import bons.furious.patch.vanilla_render_tradeoffs.ItemRenderCap;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * vanilla_item_entity_render_cap (Minecraft 1.21.1 on NeoForge 21.1.252, client; since 1.0.49; trade-off, off by default):
 *  - MixinExtras @WrapOperation on ItemEntityRenderer.render's call of the static renderMultipleFromCount (the copy loop of
 *    a dropped stack): ItemRenderCap is armed around the call, which runs unchanged.
 *  - @ModifyReturnValue on the static getRenderedAmount(count) (vanilla's 1-5 copies, the first thing renderMultipleFromCount
 *    computes): min(cap, vanilla) when armed on the render thread, vanilla's count otherwise (vault and ominous item spawner
 *    displays, any other caller).
 * Renderers that extend ItemEntityRenderer and call super.render are capped as dropped stacks; no Minecraft code is carried.
 */
@Mixin(value = ItemEntityRenderer.class, remap = false)
public abstract class ItemEntityRenderCapMixin {
    @WrapOperation(method = "render(Lnet/minecraft/world/entity/item/ItemEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/ItemEntityRenderer;renderMultipleFromCount(Lnet/minecraft/client/renderer/entity/ItemRenderer;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/item/ItemStack;Lnet/minecraft/client/resources/model/BakedModel;ZLnet/minecraft/util/RandomSource;)V"))
    private void bons$droppedStackCopies(ItemRenderer itemRenderer, PoseStack poseStack, MultiBufferSource buffers, int light, ItemStack stack,
                                         BakedModel model, boolean gui3d, RandomSource random, Operation<Void> original) {
        ItemRenderCap.arm();
        try {
            original.call(itemRenderer, poseStack, buffers, light, stack, model, gui3d, random);
        } finally {
            ItemRenderCap.disarm();
        }
    }

    @ModifyReturnValue(method = "getRenderedAmount(I)I", at = @At("RETURN"))
    private static int bons$capCopies(int vanilla) {
        return ItemRenderCap.cap(vanilla);
    }
}
