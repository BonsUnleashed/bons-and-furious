package bons.furious.mixin.iceandfire_client_c49;

import bons.furious.patch.iceandfire_client_c49.DragonLayers;
import com.github.alexthe666.iceandfire.client.render.entity.layer.LayerDragonArmor;
import com.github.alexthe666.iceandfire.entity.EntityDragonBase;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import org.spongepowered.asm.mixin.Mixin;

/**
 * iceandfire_dragon_armor_layer_ordinals (Ice and Fire 2.1.13-1.20.1-beta-5, client, since 1.0.49): LayerDragonArmor.render
 * returns at once for a dragon without armour (bons.furious.patch.iceandfire_client_c49.DragonLayers.bare); every other
 * call, and every call with the runtime flag off, runs the original.
 */
@Mixin(value = LayerDragonArmor.class, remap = false)
public abstract class LayerDragonArmorMixin {
    @WrapMethod(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILcom/github/alexthe666/iceandfire/entity/EntityDragonBase;FFFFFF)V", remap = false)
    private void bons$skipBare(PoseStack pose, MultiBufferSource buffers, int light, EntityDragonBase dragon, float limbSwing, float limbSwingAmount,
                               float partialTicks, float ageInTicks, float netHeadYaw, float headPitch, Operation<Void> original) {
        if (DragonLayers.bare(dragon)) return;
        original.call(pose, buffers, light, dragon, limbSwing, limbSwingAmount, partialTicks, ageInTicks, netHeadYaw, headPitch);
    }
}
