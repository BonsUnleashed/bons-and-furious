package bons.furious.mixin.subtleeffects;

import bons.furious.patch.subtleeffects.DryChests;
import com.llamalad7.mixinextras.sugar.Local;
import einstein.subtle_effects.ticking.tickers.BlockPosTicker;
import einstein.subtle_effects.ticking.tickers.ChestBlockEntityTicker;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * subtleeffects_dry_chest_guard (Subtle Effects 1.15.1-hotfix.1, client): a cancellable injection just before the first
 * ChestBlock.isChestBlockedAt call of ChestBlockEntityTicker.positionedTick; it returns when every later branch of the tick
 * would end in a plain return (DryChests explains the conditions and why nothing observable changes). The double-chest flag
 * and the other half's position are read from the method's own locals (MixinExtras @Local, by value). Extends BlockPosTicker
 * only to reach the inherited level and pos fields. Our own logic only (Subtle Effects is All Rights Reserved).
 */
@Mixin(value = ChestBlockEntityTicker.class, remap = false)
public abstract class ChestTickerDryGuardMixin extends BlockPosTicker {
    @Shadow
    private int ticksSinceLastAnimation;
    @Shadow
    private int animationTicks;

    private ChestTickerDryGuardMixin(Level level, BlockPos pos) {
        super(level, pos);
    }

    @Inject(method = "positionedTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/ChestBlock;isChestBlockedAt(Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/core/BlockPos;)Z", ordinal = 0), cancellable = true)
    private void bons$seDryChest(CallbackInfo ci, @Local(ordinal = 0) boolean isDouble, @Local(ordinal = 0) BlockPos other) {
        if (DryChests.skip(this.level, this.pos, isDouble, other, this.animationTicks, this.ticksSinceLastAnimation)) ci.cancel();
    }
}
