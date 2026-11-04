package bons.furious.mixin.subtleeffects_drain_c49;

import bons.furious.patch.subtleeffects_drain_c49.TickerDrain;
import einstein.subtle_effects.ticking.tickers.Ticker;
import einstein.subtle_effects.ticking.tickers.TickerManager;
import java.util.List;
import java.util.function.Consumer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * subtleeffects_ticker_removal_drain (Subtle Effects 1.15.1-hotfix.1, All Rights Reserved; client side): at the second
 * List.forEach call of TickerManager.tick(), REMOVE_QUEUE.forEach(TICKERS::remove), removes the queued tickers from TICKERS
 * in one order-keeping pass (TickerDrain) and hands forEach a consumer that does nothing; the result is the list the
 * sequential ArrayList.remove(Object) calls leave. Small batches, lists that are not plain ArrayLists and tickers whose
 * class overrides equals keep Subtle Effects' own consumer (see TickerDrain), as does the switch off.
 */
@Mixin(value = TickerManager.class, remap = false)
public abstract class TickerManagerDrainMixin {
    @Shadow
    @Final
    private static List<Ticker> TICKERS;

    @Shadow
    @Final
    private static List<Ticker> REMOVE_QUEUE;

    @ModifyArg(method = "tick()V", at = @At(value = "INVOKE", target = "Ljava/util/List;forEach(Ljava/util/function/Consumer;)V", ordinal = 1),
            require = 1, allow = 1)
    private static Consumer<? super Ticker> bons$seDrainC49Remove(Consumer<? super Ticker> action) {
        // the batch size first, here: a tick with few removals never enters TickerDrain.drain, so the JIT does not compile
        // the pass into tick() (measured: inlining it into every tick slowed the ticker loop itself)
        if (!TickerDrain.enabled || REMOVE_QUEUE.size() < TickerDrain.MIN_BATCH) return action;
        return TickerDrain.drain(TICKERS, REMOVE_QUEUE) ? TickerDrain.DONE : action;
    }
}
