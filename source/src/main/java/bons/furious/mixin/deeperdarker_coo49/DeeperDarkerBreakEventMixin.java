package bons.furious.mixin.deeperdarker_coo49;

import bons.furious.patch.deeperdarker_coo49.DeeperDarkerFixes;
import com.kyanite.deeperdarker.DeeperDarkerEvents;
import net.neoforged.neoforge.event.level.BlockEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * deeperdarker_break_block_first (Deeper and Darker, AGPL-3.0; 1.21.1 tested build: Deeper and Darker 1.4.1 for NeoForge
 * 1.21.1, deeperdarker-neoforge-1.21.1-1.4.1.jar; server): HEAD of DeeperDarkerEvents.breakEvent returns when the broken
 * state is none of crystallized amber, ice lily, ancient vase (the method then only reads the enchantment registry and the
 * main hand's Silk Touch level and fails its three tests). See DeeperDarkerFixes.
 *
 * Ported to 1.21.1: the handler reads Silk Touch through the enchantment registry lookup and ItemStack.getEnchantmentLevel
 * (Holder), and tests the same three blocks in the same branches.
 */
@Mixin(value = DeeperDarkerEvents.class, remap = false)
public abstract class DeeperDarkerBreakEventMixin {
    @Inject(method = "breakEvent", at = @At("HEAD"), cancellable = true)
    private static void bons$notADeeperDarkerBlock(BlockEvent.BreakEvent event, CallbackInfo ci) {
        if (DeeperDarkerFixes.irrelevantBreak(event.getState())) ci.cancel();
    }
}
