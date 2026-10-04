package bons.furious.mixin.ticker_gate;

import bons.furious.patch.ticker_gate.TickerContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.TickingBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * vanilla_ticker_range_memo (Minecraft 1.21.1 with NeoForge 21.1.252; both sides, acts on the server):
 * Level.tickBlockEntities stores each ticker of its list in local slot 4 (tickingblockentity) before it asks isRemoved,
 * getPos and shouldTickBlocksAt(pos). The @ModifyVariable keeps that ticker and the current thread in two fields of the
 * level as it is stored and returns it unchanged; TickerGate takes them in ServerLevel.shouldTickBlocksAt(long). A plain
 * injector at the local's STORE: no allocation. No Minecraft code.
 *
 * Ported to 1.21.1: the ticker is local slot 4 (was 3): 1.21.1 keeps tickRateManager().runsNormally() in slot 3 (a
 * boolean, istore_3) before the loop; the only astore 4 of the method is the Iterator.next() result (bytecode offset 148,
 * LVT "tickingblockentity", TickingBlockEntity). NeoForge's freshBlockEntities onLoad pass runs before the loop and stores
 * nothing in slot 4. The guard on tickBlockEntities pins that layout. Selector with descriptor.
 */
@Mixin(value = Level.class, remap = false)
public abstract class LevelTickerContextMixin implements TickerContext {
    /** The ticker the block-entity loop has just taken from the list, until the range check takes it. */
    @Unique
    private Object bons$tickerContext;
    /** The thread that stored it (the level's ticking thread). */
    @Unique
    private Thread bons$tickerThread;

    @ModifyVariable(method = "tickBlockEntities()V", at = @At("STORE"), index = 4)
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
