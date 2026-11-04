package bons.furious.mixin.lootr_c49;

import bons.furious.patch.lootr_c49.LootrEntryMemo;
import noobanidus.mods.lootr.block.entities.TileTicker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * lootr_ticker_blocker_memo (Lootr 0.7.35.94, MIT; server side): the remembered failing chunk test of a waiting chest and
 * whether Lootr's own evaluation already ran for it, as five fields on TileTicker$Entry (read and written only by
 * TileTicker.serverTick on the server thread). Lootr's own fields, equals and hashCode are untouched.
 */
@Mixin(value = TileTicker.Entry.class, remap = false)
public abstract class TileTickerEntryMemoMixin implements LootrEntryMemo {
    @Unique
    private int bons$lootrC49BlockKind;
    @Unique
    private int bons$lootrC49BlockX;
    @Unique
    private int bons$lootrC49BlockZ;
    @Unique
    private Object bons$lootrC49BlockPos;
    @Unique
    private boolean bons$lootrC49Evaluated;

    @Override
    public boolean bons$lootrC49Seen() {
        return this.bons$lootrC49Evaluated;
    }

    @Override
    public void bons$lootrC49SetSeen(boolean seen) {
        this.bons$lootrC49Evaluated = seen;
    }

    @Override
    public int bons$lootrC49Kind() {
        return this.bons$lootrC49BlockKind;
    }

    @Override
    public int bons$lootrC49X() {
        return this.bons$lootrC49BlockX;
    }

    @Override
    public int bons$lootrC49Z() {
        return this.bons$lootrC49BlockZ;
    }

    @Override
    public Object bons$lootrC49Pos() {
        return this.bons$lootrC49BlockPos;
    }

    @Override
    public void bons$lootrC49Remember(int kind, int x, int z, Object pos) {
        this.bons$lootrC49BlockKind = kind;
        this.bons$lootrC49BlockX = x;
        this.bons$lootrC49BlockZ = z;
        this.bons$lootrC49BlockPos = pos;
    }
}
