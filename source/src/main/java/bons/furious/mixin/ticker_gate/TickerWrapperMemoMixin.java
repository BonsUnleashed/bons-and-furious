package bons.furious.mixin.ticker_gate;

import bons.furious.patch.ticker_gate.TickerRangeMemo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * vanilla_ticker_range_memo (Minecraft 1.21.1 with NeoForge 21.1.252; both sides, used on the server): five fields on the
 * block-entity ticker wrapper LevelChunk$RebindableTickingBlockEntityWrapper (the object Level.tickBlockEntities iterates,
 * already in the cache when the range is checked): the tracker epoch table, chunk, tracker version, region epoch and
 * answer of its last range check (TickerGate.inRange). Adds fields only. No Minecraft code.
 *
 * Ported to 1.21.1: unchanged (the wrapper class still exists under the same name; fields and interface methods only).
 */
@Mixin(targets = "net.minecraft.world.level.chunk.LevelChunk$RebindableTickingBlockEntityWrapper", remap = false)
public abstract class TickerWrapperMemoMixin implements TickerRangeMemo {
    @Unique
    private long[] bons$rangeEpochs;
    @Unique
    private long bons$rangeChunk;
    @Unique
    private long bons$rangeVersion;
    @Unique
    private long bons$rangeStamp;
    @Unique
    private boolean bons$rangeAnswer;

    @Override
    public long[] bons$rangeEpochs() {
        return this.bons$rangeEpochs;
    }

    @Override
    public long bons$rangeChunk() {
        return this.bons$rangeChunk;
    }

    @Override
    public long bons$rangeVersion() {
        return this.bons$rangeVersion;
    }

    @Override
    public long bons$rangeStamp() {
        return this.bons$rangeStamp;
    }

    @Override
    public boolean bons$rangeAnswer() {
        return this.bons$rangeAnswer;
    }

    @Override
    public void bons$rangeRemember(long[] epochs, long chunk, long version, long stamp, boolean answer) {
        this.bons$rangeAnswer = answer;
        this.bons$rangeChunk = chunk;
        this.bons$rangeVersion = version;
        this.bons$rangeStamp = stamp;
        this.bons$rangeEpochs = epochs;
    }

    @Override
    public void bons$rangeRenew(long version) {
        this.bons$rangeVersion = version;
    }
}
