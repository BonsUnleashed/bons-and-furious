package bons.furious.mixin.netherexp_coo49;

import bons.furious.patch.netherexp_coo49.NetherExpFixes;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.jadenxgamer.netherexp.registry.item.custom.SanctumCompassItem;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * netherexp_compass_tooltip_no_tag_write (Nether Expansion 2.3.5): the one stack.getOrCreateTag() in
 * SanctumCompassItem.appendHoverText reads the existing tag or a detached empty one, so hovering never attaches a tag. See
 * NetherExpFixes. COO 5.8 hooks the same method (netherexp.MixinJneSanctumCompass, option netherexp.leanSanctumCompassTooltip).
 */
@Mixin(value = SanctumCompassItem.class, remap = false)
public abstract class SanctumCompassTooltipMixin {
    @WrapOperation(method = "m_7373_", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;m_41784_()Lnet/minecraft/nbt/CompoundTag;"),
            require = 1, allow = 1)
    private CompoundTag bons$readCompassTag(ItemStack stack, Operation<CompoundTag> original) {
        return NetherExpFixes.compassTag ? NetherExpFixes.readOnlyTag(stack) : original.call(stack);
    }
}
