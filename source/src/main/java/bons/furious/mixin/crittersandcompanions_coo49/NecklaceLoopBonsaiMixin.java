package bons.furious.mixin.crittersandcompanions_coo49;

import bons.furious.patch.crittersandcompanions_coo49.NecklaceLoop;
import io.github.bonsaistudi0s.crittersandcompanions.common.item.PearlNecklaceItem;
import java.util.Optional;
import java.util.stream.Stream;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * crittersandcompanions_necklace_loop, Critters and Companions 2.7.0 for Minecraft 1.21.1 (package io.github.bonsaistudi0s,
 * the only package C&C uses on 1.21.1): HEAD of PearlNecklaceItem.getWearing answers from NecklaceLoop over the class's own
 * getEquipment stream. See NecklaceLoop.
 */
@Mixin(value = PearlNecklaceItem.class, remap = false)
public abstract class NecklaceLoopBonsaiMixin {
    @Shadow
    private static Stream<ItemStack> getEquipment(Player player) {
        throw new AssertionError();
    }

    @Inject(method = "getWearing(Lnet/minecraft/world/entity/Entity;)Ljava/util/Optional;", at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    private static void bons$necklaceLoop(Entity entity, CallbackInfoReturnable<Optional<PearlNecklaceItem>> cir) {
        if (!NecklaceLoop.enabled) return;
        if (!(entity instanceof Player player)) {
            cir.setReturnValue(Optional.empty());
            return;
        }
        cir.setReturnValue(NecklaceLoop.wearing(getEquipment(player), PearlNecklaceItem.class, item -> ((PearlNecklaceItem) item).getLevel()));
    }
}
