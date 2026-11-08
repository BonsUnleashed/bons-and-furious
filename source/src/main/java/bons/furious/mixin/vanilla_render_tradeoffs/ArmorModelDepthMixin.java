package bons.furious.mixin.vanilla_render_tradeoffs;

import bons.furious.patch.vanilla_render_tradeoffs.ArmorBakeReuse;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.ClientHooks;
import org.spongepowered.asm.mixin.Mixin;

/**
 * vanilla_armor_layer_bake_reuse (Minecraft 1.21.1 on NeoForge 21.1.252, client; since 1.0.49; trade-off, off by default):
 * MixinExtras @WrapMethod around ClientHooks.getArmorModel (IClientItemExtensions.of(stack).getGenericArmorModel), which
 * only counts the lookup's depth on the render thread (ArmorBakeReuse) and calls the method unchanged. Injectors inside
 * the method (AzureLib's RETURN hook that hands out its own armour models) stay inside the wrapped call; callers that
 * reach getArmorModel directly (Cataclysm's armour layer) are counted like HumanoidArmorLayer.getArmorModelHook. No
 * NeoForge code is carried.
 */
@Mixin(value = ClientHooks.class, remap = false)
public abstract class ArmorModelDepthMixin {
    @WrapMethod(method = "getArmorModel")
    private static Model bons$armorLookupDepth(LivingEntity entity, ItemStack stack, EquipmentSlot slot, HumanoidModel<?> fallback,
                                               Operation<Model> original) {
        return ArmorBakeReuse.armorModel(entity, stack, slot, fallback, original);
    }
}
