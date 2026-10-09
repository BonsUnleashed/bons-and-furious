package bons.furious.mixin.vanilla_inventory_index;

import bons.furious.patch.vanilla_inventory_index.ListenerIndex;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.advancements.critereon.InventoryChangeTrigger;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;

/**
 * vanilla_inventory_trigger_index (Minecraft 1.20.1 on Forge 47.4.16; server side): InventoryChangeTrigger's private
 * trigger(player, inventory, stack, full, empty, occupied) (m_43153_) runs with the changed stack and the player's
 * PlayerAdvancements noted for this thread (ListenerIndex.enter / exit), so SimpleCriterionTrigger.trigger's listener walk
 * can use the per-item index. ServerPlayer.getAdvancements (m_8960_) is the getter trigger itself asks. The original
 * method runs unchanged. No Minecraft code.
 */
@Mixin(value = InventoryChangeTrigger.class, remap = false)
public abstract class InventoryChangeTriggerContextMixin {
    @WrapMethod(method = "m_43153_")
    private void bons$noteChange(ServerPlayer player, Inventory inventory, ItemStack stack, int full, int empty, int occupied, Operation<Void> original) {
        if (!ListenerIndex.enabled) {
            original.call(player, inventory, stack, full, empty, occupied);
            return;
        }
        Object context = ListenerIndex.enter(this, player.m_8960_(), stack);
        try {
            original.call(player, inventory, stack, full, empty, occupied);
        } finally {
            ListenerIndex.exit(context);
        }
    }
}
