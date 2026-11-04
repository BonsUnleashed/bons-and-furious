package bons.furious.mixin.crittersandcompanions_coo49;

import bons.furious.patch.crittersandcompanions_coo49.NecklaceLoop;
import java.util.Optional;
import java.util.stream.Stream;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * crittersandcompanions_necklace_loop, Critters and Companions 2.7.1 (package io.github.bonsaistudi0s, not on the build's
 * class path: string target + @Pseudo, offered to Mixin by CrittersandcompanionsPlugin only when the guard matched the 2.7.1
 * classes; the necklace type and level are reached through this mixin's own type, which Mixin maps to the target class):
 * HEAD of PearlNecklaceItem.getWearing answers from NecklaceLoop over the class's own getEquipment stream. See
 * NecklaceLoop.
 */
@Pseudo
@Mixin(targets = "io.github.bonsaistudi0s.crittersandcompanions.common.item.PearlNecklaceItem", remap = false)
public abstract class NecklaceLoopBonsaiMixin extends Item {
    private NecklaceLoopBonsaiMixin(Properties properties) {
        super(properties);
    }

    @Shadow
    private static Stream<ItemStack> getEquipment(Player player) {
        throw new AssertionError();
    }

    @Shadow
    public abstract int getLevel();

    @Inject(method = "getWearing", at = @At("HEAD"), cancellable = true)
    private static void bons$necklaceLoop(Entity entity, CallbackInfoReturnable<Optional<Object>> cir) {
        if (!NecklaceLoop.enabled) return;
        if (!(entity instanceof Player player)) {
            cir.setReturnValue(Optional.empty());
            return;
        }
        cir.setReturnValue(NecklaceLoop.wearing(getEquipment(player), NecklaceLoopBonsaiMixin.class, item -> ((NecklaceLoopBonsaiMixin) item).getLevel()));
    }
}
