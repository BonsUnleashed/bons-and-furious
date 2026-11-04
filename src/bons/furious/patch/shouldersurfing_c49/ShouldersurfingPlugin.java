package bons.furious.patch.shouldersurfing_c49;

import bons.furious.guard.Guards;
import bons.pure.config.PureMixinPlugin;
import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * Mixin config plugin of the shouldersurfing_c49 group (since 1.0.49). Everything is Bons and Furious' standard plugin
 * (switches, guard table, yields, log lines), plus one choice for shouldersurfing_item_pattern_memo, whose two mixins
 * target the class each Shoulder Surfing build keeps expressionToMatchPredicate in: UtilPatternMemoMixin (api.util.Util,
 * 5.2.0, the tested build) and AimStatePatternMemoMixin (ComputePlayerAimStateEventHandlerImpl, 5.0.11, the accepted
 * alternative build "shouldersurfing 5.0.11" in the guard table, where api.util.Util does not exist). Only the mixin of the
 * matched build is offered to Mixin, so Mixin never looks for a method that build lacks. Same delegation pattern as
 * emf_render's EmfRenderPlugin.
 */
public final class ShouldersurfingPlugin implements IMixinConfigPlugin {
    private static final String MEMO_KEY = "shouldersurfing_item_pattern_memo";
    private static final String UTIL_MEMO = "bons.furious.mixin.shouldersurfing_c49.UtilPatternMemoMixin";
    private static final String AIM_MEMO = "bons.furious.mixin.shouldersurfing_c49.AimStatePatternMemoMixin";
    /** The accepted alternative build's label in the guard table (patches/shouldersurfing_c49.json sha256_also). */
    private static final String BUILD_5011 = "shouldersurfing 5.0.11";
    private final PureMixinPlugin delegate = new PureMixinPlugin();

    @Override
    public void onLoad(String mixinPackage) {
        this.delegate.onLoad(mixinPackage);
    }

    @Override
    public String getRefMapperConfig() {
        return this.delegate.getRefMapperConfig();
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (!this.delegate.shouldApplyMixin(targetClassName, mixinClassName)) return false;
        if (UTIL_MEMO.equals(mixinClassName)) return "".equals(Guards.matchedBuild(MEMO_KEY));
        if (AIM_MEMO.equals(mixinClassName)) return BUILD_5011.equals(Guards.matchedBuild(MEMO_KEY));
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
        this.delegate.acceptTargets(myTargets, otherTargets);
    }

    @Override
    public List<String> getMixins() {
        return this.delegate.getMixins();
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        this.delegate.preApply(targetClassName, targetClass, mixinClassName, mixinInfo);
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        this.delegate.postApply(targetClassName, targetClass, mixinClassName, mixinInfo);
    }
}
