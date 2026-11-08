package bons.furious.mixin.subtleeffects_drain_c49;

import bons.furious.patch.subtleeffects_drain_c49.SeTickerFlag;
import einstein.subtle_effects.ticking.tickers.Ticker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * subtleeffects_ticker_removal_drain (Subtle Effects 1.15.1 for NeoForge 1.21.1, All Rights Reserved; client side): gives
 * every Ticker our own removal counter (SeTickerFlag), used only inside one TickerDrain pass and zero outside it. No Subtle
 * Effects field or method is read or changed.
 */
@Mixin(value = Ticker.class, remap = false)
public abstract class TickerDrainCountMixin implements SeTickerFlag {
    @Unique
    private int bons$seDrainC49Pending;

    @Override
    public void bons$seDrainC49Queue() {
        this.bons$seDrainC49Pending++;
    }

    @Override
    public boolean bons$seDrainC49Take() {
        if (this.bons$seDrainC49Pending <= 0) return false;
        this.bons$seDrainC49Pending--;
        return true;
    }

    @Override
    public void bons$seDrainC49Clear() {
        this.bons$seDrainC49Pending = 0;
    }
}
