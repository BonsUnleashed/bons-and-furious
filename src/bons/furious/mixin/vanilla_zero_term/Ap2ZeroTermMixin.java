package bons.furious.mixin.vanilla_zero_term;

import bons.furious.patch.vanilla_zero_term.ZeroTerms;
import net.minecraft.world.level.levelgen.DensityFunction;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * vanilla_density_zero_term_skip (Minecraft 1.20.1 world generation; both sides): DensityFunctions$Ap2.fillArray (m_207362_).
 * HEAD (cancellable): an addition one of whose arguments is a term multiplied by a constant 0 (see ZeroTerms) is filled the
 * short way when the context provider only positions the NoiseChunk (the NoiseChunk itself, or the column provider of
 * vanilla_noise_column_cell_fill: ZeroTerms.positioning); every other node and provider runs the original. The plan is
 * decided once per node and kept in a field (a racing first fill computes the same plan). RETURN (verify mode only): the
 * original fill is compared with the short path. compute (m_207386_) is left as shipped. Records' fields are read through
 * @Shadow (argument1 f_208398_, argument2 f_208399_). No game code is carried: our own logic only.
 */
@Mixin(targets = "net.minecraft.world.level.levelgen.DensityFunctions$Ap2", remap = false)
public abstract class Ap2ZeroTermMixin {
    @Shadow
    @Final
    private DensityFunction f_208398_;

    @Shadow
    @Final
    private DensityFunction f_208399_;

    @Unique
    private byte bons$zt;

    @Unique
    private byte bons$ztPlan() {
        byte p = this.bons$zt;
        if (p == ZeroTerms.UNDECIDED) {
            p = ZeroTerms.plan(this, this.f_208398_, this.f_208399_);
            this.bons$zt = p;
        }
        return p;
    }

    @Inject(method = "m_207362_", at = @At("HEAD"), cancellable = true)
    private void bons$ztFill(double[] ds, DensityFunction.ContextProvider cp, CallbackInfo ci) {
        if (!ZeroTerms.ACTIVE || this.bons$zt == ZeroTerms.NONE || !ZeroTerms.positioning(cp)) return;
        byte p = bons$ztPlan();
        if (p == ZeroTerms.SECOND) {
            ZeroTerms.fill(ds, cp, this.f_208398_, this.f_208399_, false);
            ci.cancel();
        } else if (p == ZeroTerms.FIRST) {
            ZeroTerms.fill(ds, cp, this.f_208399_, this.f_208398_, true);
            ci.cancel();
        }
    }

    @Inject(method = "m_207362_", at = @At("RETURN"))
    private void bons$ztVerify(double[] ds, DensityFunction.ContextProvider cp, CallbackInfo ci) {
        if (!ZeroTerms.VERIFY || this.bons$zt == ZeroTerms.NONE || !ZeroTerms.positioning(cp)) return;
        byte p = bons$ztPlan();
        if (p == ZeroTerms.SECOND) ZeroTerms.verify(ds, cp, this.f_208398_, this.f_208399_, false);
        else if (p == ZeroTerms.FIRST) ZeroTerms.verify(ds, cp, this.f_208399_, this.f_208398_, true);
    }
}
