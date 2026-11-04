package bons.furious.mixin.vanilla_render_tradeoffs;

import bons.furious.patch.vanilla_render_tradeoffs.ArmorBakeReuse;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.ForgeHooksClient;
import org.spongepowered.asm.mixin.Mixin;

/**
 * vanilla_armor_layer_bake_reuse (Forge 47.4.28, client; since 1.0.49; trade-off, off by default): MixinExtras
 * @WrapMethod around ForgeHooksClient.getArmorModel, which only counts the lookup's depth on the render thread
 * (ArmorBakeReuse) and calls the method unchanged. KnightLib's getArmorTexture hook, BCLib's and ModernFix's redirects in
 * other methods of the class are untouched. No Forge code is carried.
 */
@Mixin(value = ForgeHooksClient.class, remap = false)
public abstract class ArmorModelDepthMixin {
    @WrapMethod(method = "getArmorModel")
    private static Model bons$armorLookupDepth(LivingEntity entity, ItemStack stack, EquipmentSlot slot, HumanoidModel<?> fallback,
                                               Operation<Model> original) {
        return ArmorBakeReuse.armorModel(entity, stack, slot, fallback, original);
    }
}
