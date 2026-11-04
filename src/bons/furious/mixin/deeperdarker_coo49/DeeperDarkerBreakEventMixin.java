package bons.furious.mixin.deeperdarker_coo49;

import bons.furious.patch.deeperdarker_coo49.DeeperDarkerFixes;
import com.kyanite.deeperdarker.DeeperDarkerEvents;
import net.minecraftforge.event.level.BlockEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * deeperdarker_break_block_first (Deeper and Darker 1.3.3, server): HEAD of DeeperDarkerEvents.breakEvent returns when the
 * broken state is none of crystallized amber, ice lily, ancient vase (the method then only reads the Silk Touch level and
 * fails its three tests). See DeeperDarkerFixes. COO 5.8 hooks the same method (deeperdarker.MixinDeeperDarkerEvents,
 * option deeperdarker.leanBreakEvent); a HEAD injection composes.
 */
@Mixin(value = DeeperDarkerEvents.class, remap = false)
public abstract class DeeperDarkerBreakEventMixin {
    @Inject(method = "breakEvent", at = @At("HEAD"), cancellable = true)
    private static void bons$notADeeperDarkerBlock(BlockEvent.BreakEvent event, CallbackInfo ci) {
        if (DeeperDarkerFixes.irrelevantBreak(event.getState())) ci.cancel();
    }
}
