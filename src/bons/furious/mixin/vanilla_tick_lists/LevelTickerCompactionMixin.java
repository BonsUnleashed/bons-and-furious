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
 * vanilla_block_entity_ticker_compaction (Minecraft 1.20.1 on Forge 47.4.28, both sides; exact variant). Level.
 * tickBlockEntities (m_46463_) walks its ticker list (f_151512_) and takes each removed ticker out with Iterator.remove().
 * Three hooks, all delegating to TickerCompaction.Marks (one per level): a @Redirect of the walk's List.iterator() call
 * (the list's list iterator when this pass defers its removals), a @Redirect of the Iterator.remove() call (keeps the
 * position instead of removing), and an @Inject at RETURN (the kept positions leave the list in one pass). Both
 * redirects are require = 0: if another mod takes either call, ours steps aside and the loop removes at once as stock.
 * Radium's redirect of shouldTickBlocksAt, Bons and Furious' ticker_gate (the ticker local's STORE), KnightLib (RETURN),
 * Refurbished Furniture (the first List.isEmpty) and RyoamicLights (client) in this method are untouched. Only our own
 * logic is carried. See TickerCompaction.
 */
@Mixin(value = Level.class, remap = false)
public abstract class LevelTickerCompactionMixin {
    @Unique
    private TickerCompaction.Marks bons$tickerMarks;

    @Redirect(method = "m_46463_", require = 0, allow = 1, at = @At(value = "INVOKE", target = "Ljava/util/List;iterator()Ljava/util/Iterator;"))
    private Iterator<TickingBlockEntity> bons$startTickerWalk(List<TickingBlockEntity> list) {
        TickerCompaction.Marks marks = this.bons$tickerMarks;
        if (marks == null) {
            this.bons$tickerMarks = marks = new TickerCompaction.Marks();
        }
        return marks.start(list);
    }

    @Redirect(method = "m_46463_", require = 0, allow = 1, at = @At(value = "INVOKE", target = "Ljava/util/Iterator;remove()V"))
    private void bons$keepRemovedTicker(Iterator<?> iterator) {
        TickerCompaction.Marks marks = this.bons$tickerMarks;
        if (marks != null) {
            marks.remove(iterator);
        } else {
            iterator.remove();
        }
    }

    @Inject(method = "m_46463_", at = @At("RETURN"))
    private void bons$compactTickers(CallbackInfo ci) {
        TickerCompaction.Marks marks = this.bons$tickerMarks;
        if (marks != null) {
            marks.finish();
        }
    }
}
