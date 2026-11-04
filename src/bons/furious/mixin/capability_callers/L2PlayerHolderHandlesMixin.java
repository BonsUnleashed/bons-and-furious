package bons.furious.mixin.capability_callers;

import bons.furious.patch.capability_callers.L2LibraryHandles;
import dev.xkmc.l2library.capability.player.PlayerCapabilityHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * l2library_capability_handles (L2 Library 2.5.5, LGPL-2.1; both sides; since 1.0.49): the first player.getCapability(
 * capability) call of PlayerCapabilityHolder.get(Player) answered from the layer's kept handle (L2LibraryHandles); the revive
 * branch (reviveCaps, the second lookup, invalidateCaps) runs as shipped.
 */
@Mixin(value = PlayerCapabilityHolder.class, remap = false)
public abstract class L2PlayerHolderHandlesMixin {
    @Redirect(method = "get(Lnet/minecraft/world/entity/player/Player;)Ldev/xkmc/l2library/capability/player/PlayerCapabilityTemplate;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;getCapability(Lnet/minecraftforge/common/capabilities/Capability;)Lnet/minecraftforge/common/util/LazyOptional;",
                    ordinal = 0))
    private <T> LazyOptional<T> bons$l2PlayerHandle(Player player, Capability<T> capability) {
        return L2LibraryHandles.lookup(player, capability);
    }
}
