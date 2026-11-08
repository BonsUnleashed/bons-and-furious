package bons.furious.mixin.vanilla_tick_lists;

import bons.furious.patch.vanilla_tick_lists.TickerCompaction;
import java.util.Iterator;
import java.util.List;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.TickingBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * vanilla_block_entity_ticker_compaction (Minecraft 1.21.1 on NeoForge 21.1.252, both sides; exact variant).
 * Level.tickBlockEntities walks its ticker list (blockEntityTickers) and takes each removed ticker out with
 * Iterator.remove(). Three hooks, all delegating to TickerCompaction.Marks (one per level): a @Redirect of the walk's
 * List.iterator() call (the list's list iterator when this pass defers its removals), a @Redirect of the Iterator.remove()
 * call (keeps the position instead of removing), and an @Inject at RETURN (the kept positions leave the list in one pass).
 * Both redirects are require = 0: if another mod takes either call, ours steps aside and the loop removes at once as stock.
 * NeoForge's freshBlockEntities onLoad pass before the walk (ArrayList.forEach), Lithium 0.15.4's and Radium 0.13.1's hooks
 * on the shouldTickBlocksAt call, Bons and Furious' ticker_gate (the STORE of the ticker local, slot 4) and RyoamicLights
 * (before TickingBlockEntity.tick) are untouched. Only our own logic is carried. See TickerCompaction.
 */
@Mixin(value = Level.class, remap = false)
public abstract class LevelTickerCompactionMixin {
    @Unique
    private TickerCompaction.Marks bons$tickerMarks;

    @Redirect(method = "tickBlockEntities()V", require = 0, allow = 1, at = @At(value = "INVOKE", target = "Ljava/util/List;iterator()Ljava/util/Iterator;"))
    private Iterator<TickingBlockEntity> bons$startTickerWalk(List<TickingBlockEntity> list) {
        TickerCompaction.Marks marks = this.bons$tickerMarks;
        if (marks == null) {
            this.bons$tickerMarks = marks = new TickerCompaction.Marks();
        }
        return marks.start(list);
    }

    @Redirect(method = "tickBlockEntities()V", require = 0, allow = 1, at = @At(value = "INVOKE", target = "Ljava/util/Iterator;remove()V"))
    private void bons$keepRemovedTicker(Iterator<?> iterator) {
        TickerCompaction.Marks marks = this.bons$tickerMarks;
        if (marks != null) {
            marks.remove(iterator);
        } else {
            iterator.remove();
        }
    }

    @Inject(method = "tickBlockEntities()V", at = @At("RETURN"))
    private void bons$compactTickers(CallbackInfo ci) {
        TickerCompaction.Marks marks = this.bons$tickerMarks;
        if (marks != null) {
            marks.finish();
        }
    }
}
