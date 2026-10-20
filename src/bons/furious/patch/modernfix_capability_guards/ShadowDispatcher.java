package bons.furious.patch.modernfix_capability_guards;

import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.embeddedt.modernfix.forge.capability.GeneratedDispatcher;
import org.embeddedt.modernfix.forge.capability.analysis.CapabilityAnalysisResult;
import org.embeddedt.modernfix.forge.capability.analysis.CapabilityAnalyzer;

/**
 * modernfix_capability_guards SHADOW mode (-Dbons_and_furious.modernfixCapabilityGuards.shadow=true; verification runs
 * only). Takes the place of ModernFix's generated dispatcher for one CapabilityDispatcher and answers exactly as
 * ModernFix's GA dispatcher does: every provider is called, in order, and the first answer that is not the
 * LazyOptional.empty() singleton and is present is returned (GeneratedDispatcher.nonEmptyOrNull, the same test the
 * generated class makes), else the singleton. For each provider the switch would have skipped for the asked capability
 * (its analysis confirmed by {@link CapabilityGuards}, and the capability outside the claimed set, or the provider proven
 * always empty), it also checks that the provider's answer is that singleton: SHADOW_CHECKS counts the checks,
 * SHADOW_MISMATCHES the answers that were anything else (WARN for the first 20, naming the provider class and capability).
 */
public final class ShadowDispatcher extends GeneratedDispatcher {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    private final ICapabilityProvider[] providers;
    /** Per provider: null = never skipped; an empty set + always = skipped for every capability; else skipped outside the set. */
    private final Set<Object>[] known;
    private final boolean[] always;

    @SuppressWarnings("unchecked")
    private ShadowDispatcher(ICapabilityProvider[] providers) {
        this.providers = providers;
        this.known = new Set[providers.length];
        this.always = new boolean[providers.length];
        for (int i = 0; i < providers.length; i++) {
            Class<?> type = providers[i].getClass();
            CapabilityAnalysisResult theirs = CapabilityAnalyzer.analyze(providers[i].getClass());
            CapabilityAnalysisResult ours = CapabilityGuards.confirmed(type, theirs);
            if (ours instanceof CapabilityAnalysisResult.AlwaysEmpty) {
                always[i] = true;
                known[i] = Set.of();
            } else if (ours instanceof CapabilityAnalysisResult.KnownCapabilities k && k.capabilities().size() <= 5) {
                CapabilityGuards.Verdict v = CapabilityGuards.verdict(type, theirs);
                if (v.accepted()) known[i] = v.known();
            }
        }
    }

    static ShadowDispatcher of(ICapabilityProvider[] providers) {
        return new ShadowDispatcher(providers);
    }

    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> cap, Direction side) {
        for (int i = 0; i < providers.length; i++) {
            LazyOptional<T> r = providers[i].getCapability(cap, side);
            Set<Object> k = known[i];
            if (k != null && (always[i] || !k.contains(cap))) {
                CapabilityGuards.SHADOW_CHECKS.incrementAndGet();
                if (r != LazyOptional.empty()) {
                    long n = CapabilityGuards.SHADOW_MISMATCHES.incrementAndGet();
                    if (n <= 20) LOGGER.warn("Bons and Furious: modernfix_capability_guards shadow mismatch #{}: {} answered {} for {} (it would have been skipped)",
                            n, providers[i].getClass().getName(), r, cap == null ? "null" : cap.getName());
                }
            }
            LazyOptional<T> found = nonEmptyOrNull(r);
            if (found != null) return found;
        }
        return LazyOptional.empty();
    }
}
