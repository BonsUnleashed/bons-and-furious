package bons.furious.mixin.elysium_replacer;

import bons.furious.patch.elysium_replacer.ElysiumLean;
import com.bawnorton.mixinsquared.TargetHandler;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.jadenxgamer.elysium_api.impl.biome.ElysiumBiomeHelper;
import net.jadenxgamer.elysium_api.impl.biome.ElysiumBiomeSource;
import net.jadenxgamer.elysium_api.impl.compat.ElysiumTerrablenderHelper;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * elysium_lean_replacer (ElysiumAPI 1.1.3, both sides): MixinSquared @TargetHandler injections into ElysiumAPI's
 * MultiNoiseBiomeSourceMixin.elysium$getNoiseBiome handler, merged into MultiNoiseBiomeSource.getNoiseBiome (m_203407_).
 *
 * HEAD (cancellable): the handler runs here with ElysiumLean's lean bookkeeping and the same decisions - the current
 * biome computed as Elysium computes it, the same replacer checks for sources that have a dimension, the replacement set
 * on Elysium's own callback when it differs - and Elysium's body is skipped. ElysiumAPI is GPL-2.0 / LGPL-2.1: this is a
 * modified version of its handler (see ElysiumLean for what changed and why the result is the same). In shadow mode the
 * body runs as shipped; the call to its getCurrentBiome is wrapped to remember the biome it computed, and at its RETURN
 * the lean decision is compared with what it did. Priority 1500: applied after ElysiumAPI's mixin (priority 996).
 *
 * Since 1.0.38 the HEAD injection is optional like the other two (require = 0): Collections Of Optimizations 4.6 cancels
 * ElysiumAPI's mixin through MixinSquared, so its handler never reaches the class and a required injection stopped the
 * game at start. The switch's guard steps aside first when it sees such a canceller (Guards.cancelledDecision); if a
 * handler is missing anyway, nothing here is injected and postApply logs it (Guards.HANDLER_HOST).
 */
@Mixin(value = MultiNoiseBiomeSource.class, priority = 1500, remap = false)
public abstract class ElysiumLeanHandlerMixin {
    @Unique
    private static final ThreadLocal<Holder<Biome>> bons$elShadowCurrent = new ThreadLocal<>();

    @TargetHandler(mixin = "net.jadenxgamer.elysium_api.impl.mixin.biome.MultiNoiseBiomeSourceMixin", name = "elysium$getNoiseBiome")
    @Inject(method = "@MixinSquared:Handler", at = @At("HEAD"), cancellable = true, require = 0, expect = 0)
    private void bons$elLean(int x, int y, int z, Climate.Sampler sampler, CallbackInfoReturnable<Holder<Biome>> theirs, CallbackInfo ci) {
        if (!ElysiumLean.enabled || ElysiumLean.SHADOW) return;
        Holder<Biome> current = ElysiumLean.terraBlender()
                ? ElysiumTerrablenderHelper.getCurrentBiome((MultiNoiseBiomeSource) (Object) this, x, y, z, sampler)
                : (Holder<Biome>) ((MultiNoiseParametersInvoker) (Object) this).bons$elParameters().m_204252_(sampler.m_183445_(x, y, z));
        if ((Object) this instanceof ElysiumBiomeSource source && source.getDimension() != null) {
            Holder<Biome> replaced = ElysiumLean.replace(x, z, current, ElysiumBiomeHelper.biomesForDimension(source.getDimension()),
                    ElysiumLean.dataDriven(), source.getWorldSeed());
            if (!replaced.equals(current)) theirs.setReturnValue(replaced);
        }
        ci.cancel();
    }

    /** Shadow mode only: remember the biome Elysium's handler computed. */
    @TargetHandler(mixin = "net.jadenxgamer.elysium_api.impl.mixin.biome.MultiNoiseBiomeSourceMixin", name = "elysium$getNoiseBiome")
    @WrapOperation(method = "@MixinSquared:Handler", require = 0, expect = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/biome/MultiNoiseBiomeSource;getCurrentBiome(IIILnet/minecraft/world/level/biome/Climate$Sampler;)Lnet/minecraft/core/Holder;"))
    private Holder<Biome> bons$elShadowCapture(MultiNoiseBiomeSource source, int x, int y, int z, Climate.Sampler sampler, Operation<Holder<Biome>> original) {
        Holder<Biome> current = original.call(source, x, y, z, sampler);
        if (ElysiumLean.SHADOW) bons$elShadowCurrent.set(current);
        return current;
    }

    /** Shadow mode only: compare the lean decision with what Elysium's handler did. */
    @TargetHandler(mixin = "net.jadenxgamer.elysium_api.impl.mixin.biome.MultiNoiseBiomeSourceMixin", name = "elysium$getNoiseBiome")
    @Inject(method = "@MixinSquared:Handler", at = @At("RETURN"), require = 0, expect = 0)
    private void bons$elShadowCompare(int x, int y, int z, Climate.Sampler sampler, CallbackInfoReturnable<Holder<Biome>> theirs, CallbackInfo ci) {
        if (!ElysiumLean.SHADOW) return;
        Holder<Biome> current = bons$elShadowCurrent.get();
        bons$elShadowCurrent.remove();
        if (current == null || !((Object) this instanceof ElysiumBiomeSource source) || source.getDimension() == null) return;
        ElysiumLean.shadowCompare(x, z, current, ElysiumBiomeHelper.biomesForDimension(source.getDimension()), source.getWorldSeed(),
                theirs.isCancelled() ? theirs.getReturnValue() : null);
    }
}
