package bons.furious.mixin.vanilla_c2;

import bons.furious.patch.vanilla_c2.CursorCounters;
import net.minecraft.core.Cursor3D;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * vanilla_cursor_counters (Minecraft 1.20.1, both sides; SRG client jar 1.20.1-20230612.114412, not patched by Forge
 * 47.4.16). The constructor decides the mode once (CursorCounters.counterMode: three positive sizes, cell count without int
 * overflow); in counter mode advance() (m_122304_) is answered at its head by stepping x and carrying into y and z, leaving
 * the same x, y, z and index the original's divisions would; in any other mode the injection returns at once and the
 * original advance runs in place. Only our own logic is carried; no other mod mixes into Cursor3D. See CursorCounters.
 */
@Mixin(value = Cursor3D.class, remap = false)
public abstract class Cursor3DCounterMixin {
    @Shadow
    @Final
    private int f_122289_;   // width
    @Shadow
    @Final
    private int f_122290_;   // height
    @Shadow
    @Final
    private int f_122291_;   // depth
    @Shadow
    @Final
    private int f_122292_;   // cell count
    @Shadow
    private int f_122293_;   // index
    @Shadow
    private int f_122294_;   // x
    @Shadow
    private int f_122295_;   // y
    @Shadow
    private int f_122296_;   // z
    @Unique
    private boolean bons$counter;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void bons$counterMode(int minX, int minY, int minZ, int maxX, int maxY, int maxZ, CallbackInfo ci) {
        this.bons$counter = CursorCounters.counterMode(this.f_122289_, this.f_122290_, this.f_122291_, this.f_122292_);
    }

    @Inject(method = "m_122304_", at = @At("HEAD"), cancellable = true)
    private void bons$advance(CallbackInfoReturnable<Boolean> cir) {
        if (!this.bons$counter) return;
        int index = this.f_122293_;
        if (index == this.f_122292_) {
            cir.setReturnValue(false);
            return;
        }
        // the cell of `index`: (0, 0, 0) for the first, otherwise the previous cell plus one in x, carrying into y and z
        if (index != 0 && ++this.f_122294_ == this.f_122289_) {
            this.f_122294_ = 0;
            if (++this.f_122295_ == this.f_122290_) {
                this.f_122295_ = 0;
                ++this.f_122296_;
            }
        }
        this.f_122293_ = index + 1;
        cir.setReturnValue(true);
    }
}
