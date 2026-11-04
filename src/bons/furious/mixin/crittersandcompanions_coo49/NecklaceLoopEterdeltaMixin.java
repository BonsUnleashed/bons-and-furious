package bons.furious.mixin.crittersandcompanions_coo49;

import bons.furious.patch.crittersandcompanions_coo49.NecklaceLoop;
import com.github.eterdelta.crittersandcompanions.item.PearlNecklaceItem;
import java.util.Optional;
import java.util.stream.Stream;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * crittersandcompanions_necklace_loop, Critters and Companions 2.3.5 (package com.github.eterdelta; absent in 2.7.1 packs,
 * so the mixin is @Pseudo and CrittersandcompanionsPlugin offers it to Mixin only when the guard matched the 2.3.5
 * classes): HEAD of PearlNecklaceItem.getWearing answers from NecklaceLoop over the class's own getEquipment stream. See
 * NecklaceLoop.
 */
@Pseudo
@Mixin(value = PearlNecklaceItem.class, remap = false)
public abstract class NecklaceLoopEterdeltaMixin {
    @Shadow
    private static Stream<ItemStack> getEquipment(Player player) {
        throw new AssertionError();
    }

    @Inject(method = "getWearing", at = @At("HEAD"), cancellable = true)
    private static void bons$necklaceLoop(Entity entity, CallbackInfoReturnable<Optional<PearlNecklaceItem>> cir) {
        if (!NecklaceLoop.enabled) return;
        if (!(entity instanceof Player player)) {
            cir.setReturnValue(Optional.empty());
            return;
        }
        cir.setReturnValue(NecklaceLoop.wearing(getEquipment(player), PearlNecklaceItem.class, item -> ((PearlNecklaceItem) item).getLevel()));
    }
}
