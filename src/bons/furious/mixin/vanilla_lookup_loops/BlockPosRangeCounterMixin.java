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
 * vanilla_block_range_counters (Minecraft 1.20.1, SRG client jar 1.20.1-20230612.114412, not patched by Forge 47.4.28; both
 * sides). Target: the iterator of BlockPos.betweenClosed(int...) (m_121976_), the anonymous BlockPos$3. A cancellable HEAD
 * injection into its computeNext: at the first position the iterator decides once (RangeCounters.counterMode) whether it
 * steps three counters (x, carrying into y and z) instead of dividing its running index; in counter mode it moves the cursor
 * to the same position through the same MutableBlockPos.set (m_122178_) and advances the index exactly as the original
 * does; otherwise (and at the end of the range) the original body runs in place. betweenClosed itself is untouched. Only
 * our own logic is carried. See RangeCounters.
 */
@Mixin(targets = "net.minecraft.core.BlockPos$3", remap = false)
public abstract class BlockPosRangeCounterMixin {
    @Shadow
    @Final
    private BlockPos.MutableBlockPos f_122090_;   // the cursor handed out
    @Shadow
    private int f_122091_;                         // running index
    @Shadow
    @Final
    int f_122084_;                                 // position count (width * height * depth as an int)
    @Shadow
    @Final
    int f_122085_;                                 // width (x)
    @Shadow
    @Final
    int f_122086_;                                 // height (y)
    @Shadow
    @Final
    int f_122087_;                                 // min x
    @Shadow
    @Final
    int f_122088_;                                 // min y
    @Shadow
    @Final
    int f_122089_;                                 // min z
    /** Digits of the index the next step hands out; bons$z == RangeCounters.STOCK marks an iterator on the original code. */
    @Unique
    private int bons$x, bons$y, bons$z;

    @Inject(method = "computeNext()Lnet/minecraft/core/BlockPos;", at = @At("HEAD"), cancellable = true)
    private void bons$counterStep(CallbackInfoReturnable<BlockPos> cir) {
        int index = this.f_122091_;
        if (index == this.f_122084_) return;   // the original ends the range
        int x, y, z;
        if (index == 0) {
            if (!RangeCounters.counterMode(this.f_122085_, this.f_122086_, this.f_122084_)) {
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
        this.f_122091_ = index + 1;
        int nx = x + 1, ny = y, nz = z;
        if (nx == this.f_122085_) {
            nx = 0;
            if (++ny == this.f_122086_) {
                ny = 0;
                ++nz;
            }
        }
        this.bons$x = nx;
        this.bons$y = ny;
        this.bons$z = nz;
        cir.setReturnValue(this.f_122090_.m_122178_(this.f_122087_ + x, this.f_122088_ + y, this.f_122089_ + z));
    }
}
