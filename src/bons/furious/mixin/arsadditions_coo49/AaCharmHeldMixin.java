package bons.furious.mixin.arsadditions_coo49;

import bons.furious.patch.arsadditions_coo49.AaCharms;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * arsadditions_charm_held_only_inventory_tick (Ars Additions 1.20.1-1.6.7, LGPL-3.0: no code carried; gameplay fix, off by
 * default). Charm.inventoryTick returns at once for a stack that is in neither hand of the living holder
 * (AaCharms.skipStowed); held charms tick as before, equipped ones keep their curio tick.
 */
@Mixin(targets = "com.github.jarva.arsadditions.common.item.curios.Charm", remap = false)
public abstract class AaCharmHeldMixin {
    @Inject(method = "m_6883_(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/Entity;IZ)V",
            at = @At("HEAD"), cancellable = true)
    private void bons$arsadditionsHeldCharmTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected, CallbackInfo ci) {
        if (AaCharms.skipStowed(stack, entity)) ci.cancel();
    }
}
