package bons.furious.mixin.oddaccessories_coo49;

import bons.furious.patch.oddaccessories_coo49.OddScans;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * oddaccessories_cloak_action_bar_interval (Odd Accessories 1.1.6; server side; trade-off, off by default): MixinExtras
 * @WrapOperation on the two Player.displayClientMessage(text, true) (m_5661_) calls of FiberglassCloakItem.onCurioTick, the
 * cloak's action-bar indicator sent every tick ("(⭘)" while too many mobs are close, "(-)" while cloaked): sent at once when
 * it differs from the one last sent to the player, then again only every N ticks while it stays the same
 * (OddScans.cloakMessage). Our own logic only.
 */
@Mixin(targets = "net.oddaccessories.item.FiberglassCloakItem", remap = false)
public abstract class OddCloakActionBarMixin {
    @WrapOperation(method = "onCurioTick(Ltop/theillusivec4/curios/api/SlotContext;Lnet/minecraft/world/item/ItemStack;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;m_5661_(Lnet/minecraft/network/chat/Component;Z)V", ordinal = 0),
            require = 1, allow = 1)
    private void bons$oddaccessoriesCoo49BarBusy(Player player, Component text, boolean actionBar, Operation<Void> original) {
        OddScans.cloakMessage(player, text, actionBar, original, OddScans.BAR_BUSY);
    }

    @WrapOperation(method = "onCurioTick(Ltop/theillusivec4/curios/api/SlotContext;Lnet/minecraft/world/item/ItemStack;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;m_5661_(Lnet/minecraft/network/chat/Component;Z)V", ordinal = 1),
            require = 1, allow = 1)
    private void bons$oddaccessoriesCoo49BarCloaked(Player player, Component text, boolean actionBar, Operation<Void> original) {
        OddScans.cloakMessage(player, text, actionBar, original, OddScans.BAR_CLOAKED);
    }
}
