package bons.furious.guard;

import com.bawnorton.mixinsquared.api.MixinCanceller;
import java.util.List;

/**
 * Registered with MixinSquared (META-INF/services). Cancels another mod's mixin only while the Bons and Furious
 * switch that replaces its behaviour is enabled and its fingerprints match; see Guards.
 */
public final class FuriousMixinCanceller implements MixinCanceller {
    @Override
    public boolean shouldCancel(List<String> targetClassNames, String mixinClassName) {
        return Guards.shouldCancel(mixinClassName);
    }
}
