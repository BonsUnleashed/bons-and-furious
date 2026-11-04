package bons.furious.patch.celestialcore_match_c49;

import bons.furious.guard.Guards;
import bons.pure.config.PureMixinPlugin;
import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * Mixin config plugin of the celestialcore_match_c49 group (since 1.0.49). Everything is Bons and Furious' standard plugin
 * (switches, guard table, yields, log lines), plus one choice for celestialcore_transformation_recipe_match, whose recipe
 * mixin comes per Celestial Core build: FluidRecipeBlockMixin for FluidTransformationRecipe (1.6.x, the tested build) and
 * StatRecipeBlockMixin for TransformationRecipe (1.5.x, the accepted alternative build "celestial_core 1.5.8"). Only the
 * mixin whose class the matched build has is offered to Mixin, so Mixin never looks for the class that build lacks (that
 * lookup logs "Error loading class" even for a @Pseudo mixin). Same delegation pattern as emf_render's EmfRenderPlugin.
 */
public final class CelestialcoreMatchPlugin implements IMixinConfigPlugin {
    private static final String KEY = "celestialcore_transformation_recipe_match";
    private static final String PKG = "bons.furious.mixin.celestialcore_match_c49.";
    private static final String FLUID = PKG + "FluidRecipeBlockMixin", STAT = PKG + "StatRecipeBlockMixin";
    private static final String OLD_BUILD = "celestial_core 1.5.8";
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
        if (!mixinClassName.equals(FLUID) && !mixinClassName.equals(STAT)) return true;
        String build = Guards.matchedBuild(KEY);
        return mixinClassName.equals(FLUID) ? "".equals(build) : OLD_BUILD.equals(build);
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
