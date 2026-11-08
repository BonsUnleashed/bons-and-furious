package bons.furious.mixin.vanilla_lookup_loops;

import bons.furious.patch.vanilla_lookup_loops.RangeCounters;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * vanilla_block_range_counters (Minecraft 1.21.1, both sides; BlockPos is not patched by NeoForge 21.1.252). Target: the
 * iterator of BlockPos.betweenClosed(int...), the anonymous BlockPos$4 (BlockPos$1 is the STREAM_CODEC, $2 and $3 are the
 * iterators of randomBetweenClosed and withinManhattan). A cancellable HEAD injection into its computeNext: at the first
 * position the iterator decides once (RangeCounters.counterMode) whether it steps three counters (x, carrying into y and z)
 * instead of dividing its running index; in counter mode it moves the cursor to the same position through the same
 * MutableBlockPos.set and advances the index exactly as the original does; otherwise (and at the end of the range) the
 * original body runs in place. betweenClosed and the lambda that builds the iterator are untouched.
 *
 * The values the iterator captures (position count, width, height and the minimum corner) are synthetic fields named after
 * the compiler's local variables: the NeoForm recompilation (the development build) calls them val$l, val$i, val$j,
 * val$p_121977_, val$p_121978_ and val$p_121979_; Mojang's class, which the installed game runs, calls them val$end,
 * val$width, val$height, val$minX, val$minY and val$minZ, with the same code otherwise. The shadows carry Mojang's names as
 * aliases, so the mixin binds to either class. Only our own logic is carried. See RangeCounters.
 */
@Mixin(targets = "net.minecraft.core.BlockPos$4", remap = false)
public abstract class BlockPosRangeCounterMixin {
    @Shadow
    @Final
    private BlockPos.MutableBlockPos cursor;   // the cursor handed out
    @Shadow
    private int index;                         // running index
    @Shadow(aliases = "val$end")
    @Final
    private int val$l;                         // position count (width * height * depth as an int)
    @Shadow(aliases = "val$width")
    @Final
    private int val$i;                         // width (x)
    @Shadow(aliases = "val$height")
    @Final
    private int val$j;                         // height (y)
    @Shadow(aliases = "val$minX")
    @Final
    private int val$p_121977_;                 // min x
    @Shadow(aliases = "val$minY")
    @Final
    private int val$p_121978_;                 // min y
    @Shadow(aliases = "val$minZ")
    @Final
    private int val$p_121979_;                 // min z
    /** Digits of the index the next step hands out; bons$z == RangeCounters.STOCK marks an iterator on the original code. */
    @Unique
    private int bons$x, bons$y, bons$z;

    @Inject(method = "computeNext()Lnet/minecraft/core/BlockPos;", at = @At("HEAD"), cancellable = true)
    private void bons$counterStep(CallbackInfoReturnable<BlockPos> cir) {
        int index = this.index;
        int end = this.val$l, width = this.val$i, height = this.val$j;
        if (index == end) return;   // the original ends the range
        int x, y, z;
        if (index == 0) {
            if (!RangeCounters.counterMode(width, height, end)) {
                this.bons$z = RangeCounters.STOCK;
                return;
            }
            x = 0;
            y = 0;
            z = 0;
        } else {
            z = this.bons$z;
            if (z == RangeCounters.STOCK) return;
            x = this.bons$x;
            y = this.bons$y;
        }
        this.index = index + 1;
        int nx = x + 1, ny = y, nz = z;
        if (nx == width) {
            nx = 0;
            if (++ny == height) {
                ny = 0;
                ++nz;
            }
        }
        this.bons$x = nx;
        this.bons$y = ny;
        this.bons$z = nz;
        cir.setReturnValue(this.cursor.set(this.val$p_121977_ + x, this.val$p_121978_ + y, this.val$p_121979_ + z));
    }
}
