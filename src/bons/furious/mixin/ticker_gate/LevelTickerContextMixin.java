package bons.furious.mixin.ticker_gate;

import bons.furious.patch.ticker_gate.TickerContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.TickingBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * vanilla_ticker_range_memo (Minecraft 1.20.1 on Forge 47.4.16; both sides, acts on the server): Level.tickBlockEntities
 * (m_46463_) stores each ticker of its list in local slot 3 (tickingblockentity) before it asks isRemoved, getPos and
 * shouldTickBlocksAt(pos). The @ModifyVariable keeps that ticker and the current thread in two fields of the level as it
 * is stored and returns it unchanged; TickerGate takes them in ServerLevel.shouldTickBlocksAt(long). A plain injector at
 * the local's STORE: no allocation, and no other mixin in the pack targets this local (Radium's sleeping option redirects
 * the shouldTickBlocksAt call itself and stays compatible; RyoamicLights captures this local at the tick() call, knightlib
 * injects at RETURN). No Minecraft code.
 */
@Mixin(value = Level.class, remap = false)
public abstract class LevelTickerContextMixin implements TickerContext {
    /** The ticker the block-entity loop has just taken from the list, until the range check takes it. */
    @Unique
    private Object bons$tickerContext;
    /** The thread that stored it (the level's ticking thread). */
    @Unique
    private Thread bons$tickerThread;

    @ModifyVariable(method = "m_46463_", at = @At("STORE"), index = 3)
    private TickingBlockEntity bons$loopTicker(TickingBlockEntity ticker) {
        this.bons$tickerContext = ticker;
        this.bons$tickerThread = Thread.currentThread();
        return ticker;
    }

    @Override
    public Object bons$tickerContext() {
        return this.bons$tickerContext;
    }

    @Override
    public Thread bons$tickerThread() {
        return this.bons$tickerThread;
    }

    @Override
    public void bons$clearTickerContext() {
        this.bons$tickerContext = null;
    }
}
