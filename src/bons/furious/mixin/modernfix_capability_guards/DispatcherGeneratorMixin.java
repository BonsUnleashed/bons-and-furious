package bons.furious.mixin.modernfix_capability_guards;

import bons.furious.patch.modernfix_capability_guards.CapabilityGuards;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import org.embeddedt.modernfix.forge.capability.CapabilityProviderDispatcherGenerator;
import org.embeddedt.modernfix.forge.capability.GeneratedDispatcher;
import org.embeddedt.modernfix.forge.capability.analysis.CapabilityAnalysisResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * modernfix_capability_guards (ModernFix 5.27.77 and 5.27.89, LGPL-3.0; both sides). Two hooks in ModernFix's
 * CapabilityProviderDispatcherGenerator, our logic only (CapabilityGuards):
 * <ul>
 *   <li>@WrapMethod on the one-argument getOrGenerateDispatcher(ICapabilityProvider[]) (called by ModernFix's
 *       CapabilityDispatcher constructor hook): with the switch on, the two-argument overload is asked with analysis on;
 *       provider order, ModernFix's generated code and its cache (keyed by the flag) are ModernFix's own;</li>
 *   <li>@WrapOperation on the CapabilityAnalyzer.analyze(Class) call in generateClass: ModernFix's result is kept only
 *       when our proof confirms it, else the provider is Indeterminate (called for every capability, as at GA).</li>
 * </ul>
 * ModernFix's AttachCapabilitiesEvent re-sort (BETA) is never enabled. No ModernFix code is carried.
 */
@Mixin(value = CapabilityProviderDispatcherGenerator.class, remap = false)
public abstract class DispatcherGeneratorMixin {
    @WrapMethod(method = "getOrGenerateDispatcher([Lnet/minecraftforge/common/capabilities/ICapabilityProvider;)Lorg/embeddedt/modernfix/forge/capability/GeneratedDispatcher;")
    private static GeneratedDispatcher bons$guardedDispatcher(ICapabilityProvider[] providers, Operation<GeneratedDispatcher> original) {
        return CapabilityGuards.dispatcher(providers, original);
    }

    @WrapOperation(method = "generateClass", at = @At(value = "INVOKE",
            target = "Lorg/embeddedt/modernfix/forge/capability/analysis/CapabilityAnalyzer;analyze(Ljava/lang/Class;)Lorg/embeddedt/modernfix/forge/capability/analysis/CapabilityAnalysisResult;"))
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static CapabilityAnalysisResult bons$provenAnalysis(Class type, Operation<CapabilityAnalysisResult> original) {
        return CapabilityGuards.analyze(type, original);
    }
}
