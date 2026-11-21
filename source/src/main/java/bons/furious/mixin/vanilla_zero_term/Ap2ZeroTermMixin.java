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
 * vanilla_density_zero_term_skip (Minecraft 1.21.1 world generation, tested build NeoForge 21.1.252; both sides):
 * DensityFunctions$Ap2.fillArray.
 * HEAD (cancellable): an addition one of whose arguments is a term multiplied by a constant 0 (see ZeroTerms) is filled the
 * short way when the context provider only positions the NoiseChunk (the NoiseChunk itself, or the column provider of
 * vanilla_noise_column_cell_fill: ZeroTerms.positioning); every other node and provider runs the original. The plan is
 * decided once per node and kept in a field (a racing first fill computes the same plan). RETURN (verify mode only): the
 * original fill is compared with the short path. compute is left as shipped. Records' fields are read through @Shadow
 * (argument1, argument2). No game code is carried: our own logic only.
 *
 * Ported to 1.21.1: DensityFunctions$Ap2.fillArray, $MulOrAdd.transform, $PureTransformer.compute / fillArray and
 * NoiseChunk.forIndex make the same calls and field accesses as on 1.20.1 (the 1.21.1 build compiles the enum switches of
 * the first two without a switch map); Mojang names, nothing else changed.
 */
@Mixin(targets = "net.minecraft.world.level.levelgen.DensityFunctions$Ap2", remap = false)
public abstract class Ap2ZeroTermMixin {
    @Shadow
    @Final
    private DensityFunction argument1;

    @Shadow
    @Final
    private DensityFunction argument2;

    @Unique
    private byte bons$zt;

    @Unique
    private byte bons$ztPlan() {
        byte p = this.bons$zt;
        if (p == ZeroTerms.UNDECIDED) {
            p = ZeroTerms.plan(this, this.argument1, this.argument2);
            this.bons$zt = p;
        }
        return p;
    }

    @Inject(method = "fillArray", at = @At("HEAD"), cancellable = true)
    private void bons$ztFill(double[] ds, DensityFunction.ContextProvider cp, CallbackInfo ci) {
        if (!ZeroTerms.ACTIVE || this.bons$zt == ZeroTerms.NONE || !ZeroTerms.positioning(cp)) return;
        byte p = bons$ztPlan();
        if (p == ZeroTerms.SECOND) {
            ZeroTerms.fill(ds, cp, this.argument1, this.argument2, false);
            ci.cancel();
        } else if (p == ZeroTerms.FIRST) {
            ZeroTerms.fill(ds, cp, this.argument2, this.argument1, true);
            ci.cancel();
        }
    }

    @Inject(method = "fillArray", at = @At("RETURN"))
    private void bons$ztVerify(double[] ds, DensityFunction.ContextProvider cp, CallbackInfo ci) {
        if (!ZeroTerms.VERIFY || this.bons$zt == ZeroTerms.NONE || !ZeroTerms.positioning(cp)) return;
        byte p = bons$ztPlan();
        if (p == ZeroTerms.SECOND) ZeroTerms.verify(ds, cp, this.argument1, this.argument2, false);
        else if (p == ZeroTerms.FIRST) ZeroTerms.verify(ds, cp, this.argument2, this.argument1, true);
    }
}
