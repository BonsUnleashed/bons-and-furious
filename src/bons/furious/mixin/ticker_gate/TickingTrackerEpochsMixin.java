package bons.furious.mixin.ticker_gate;

import bons.furious.patch.ticker_gate.TickerGate;
import bons.furious.patch.ticker_gate.TrackerEpochs;
import net.minecraft.server.level.TickingTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * vanilla_ticker_range_memo (Minecraft 1.20.1; server): TickingTracker keeps a write version and one long epoch per group of
 * 8x8-chunk regions. setLevel (m_7351_), the only code that writes the ticking-level map (f_184141_; no other class in the
 * pack names that field), bumps the version and the group of its chunk at its start and again at its return, so an answer
 * taken while a write is pending is not used afterwards either. setLevel runs during ticket propagation, not per block
 * entity, so the CallbackInfo does not matter. vanilla_block_ticking_range_memo injects at the same two points (its own
 * version counter); the injections compose. No Minecraft code.
 */
@Mixin(value = TickingTracker.class, remap = false)
public abstract class TickingTrackerEpochsMixin implements TrackerEpochs {
    @Unique
    private final long[] bons$tickerEpochs = TickerGate.newEpochs();
    @Unique
    private long bons$tickerVersion = 1L;

    @Override
    public long[] bons$tickerEpochs() {
        return this.bons$tickerEpochs;
    }

    @Override
    public long bons$tickerVersion() {
        return this.bons$tickerVersion;
    }

    @Inject(method = "m_7351_", at = @At("HEAD"))
    private void bons$beforeLevelWrite(long chunk, int level, CallbackInfo ci) {
        this.bons$tickerVersion++;
        TickerGate.bump(this.bons$tickerEpochs, chunk);
    }

    @Inject(method = "m_7351_", at = @At("RETURN"))
    private void bons$afterLevelWrite(long chunk, int level, CallbackInfo ci) {
        this.bons$tickerVersion++;
        TickerGate.bump(this.bons$tickerEpochs, chunk);
    }
}
