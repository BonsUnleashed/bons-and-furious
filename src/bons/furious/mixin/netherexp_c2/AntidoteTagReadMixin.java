package bons.furious.mixin.netherexp_c2;

import bons.furious.patch.netherexp_c2.AntidoteGuard;
import net.jadenxgamer.netherexp.registry.item.custom.AntidoteItem;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * netherexp_antidote_effect_guard (Jaden's Nether Expansion 2.3.5, both sides; a fix), since 1.0.49: the stack.getOrCreateTag()
 * reads of AntidoteItem.getAntidoteEffect, getColor, getDuration (static) and of appendHoverText (m_7373_) and
 * getDescriptionId (m_5671_, the item name) read the stack's tag without creating one (AntidoteGuard.readTag): drawing,
 * naming or describing a tagless antidote no longer attaches an empty tag to the stack. finishUsingItem is unchanged. Two
 * handlers because Mixin wants a static handler for the static methods and an instance one for the others. Our own logic
 * only (CC-BY-NC-SA-4.0 target).
 */
@Mixin(value = AntidoteItem.class, remap = false)
public abstract class AntidoteTagReadMixin {
    @Redirect(method = {"getAntidoteEffect", "getColor", "getDuration"}, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/ItemStack;m_41784_()Lnet/minecraft/nbt/CompoundTag;"), require = 4, allow = 4)
    private static CompoundTag bons$readAntidoteTag(ItemStack stack) {
        return AntidoteGuard.readTag(stack);
    }

    @Redirect(method = {"m_7373_", "m_5671_"}, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/ItemStack;m_41784_()Lnet/minecraft/nbt/CompoundTag;"), require = 2, allow = 2)
    private CompoundTag bons$readAntidoteTagOfItem(ItemStack stack) {
        return AntidoteGuard.readTag(stack);
    }
}
