package bons.furious.patch.bloodmagic_coo49;

import bons.pure.config.PureMixinPlugin;
import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * Mixin config plugin of the bloodmagic_coo49 group (since 1.0.49). Everything is Bons and Furious' standard plugin
 * (switches, guard table, yields, log lines); in addition, after the list memo's probe mixin BmRecipeManagerProbeMixin has
 * been applied to RecipeManager (priority 3000, so every other mod's mixin on the class is already there), BmProbe records
 * whether getAllRecipesFor and byType are still as shipped. Same delegation pattern as vanilla_cooking_recipe_index's
 * RecipeIndexPlugin.
 */
public final class BmPlugin implements IMixinConfigPlugin {
    private static final String PROBE = "bons.furious.mixin.bloodmagic_coo49.BmRecipeManagerProbeMixin";
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
        return this.delegate.shouldApplyMixin(targetClassName, mixinClassName);
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
        if (PROBE.equals(mixinClassName)) BmProbe.record(targetClass);
    }
}
