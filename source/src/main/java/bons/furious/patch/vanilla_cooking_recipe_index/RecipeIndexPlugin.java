package bons.furious.patch.vanilla_cooking_recipe_index;

import bons.pure.config.PureMixinPlugin;
import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * Mixin config plugin of the vanilla_cooking_recipe_index group (since 1.0.49). Everything is Bons and Furious' standard
 * plugin (switches, guard table, yields, log lines); in addition, after each of the layer's three probe mixins has been applied
 * (they have priority 3000, so every other mod's mixin on the class is already there), RecipeIndexProbe records whether the
 * methods the index relies on are still as shipped. Same delegation pattern as mcreator_entity_guards' EntityGuardsPlugin.
 */
public final class RecipeIndexPlugin implements IMixinConfigPlugin {
    private static final String PKG = "bons.furious.mixin.vanilla_cooking_recipe_index.";
    private static final Set<String> PROBES = Set.of(PKG + "RecipeManagerIndexMixin", PKG + "AbstractCookingRecipeProbeMixin", PKG + "IngredientProbeMixin");
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
        if (PROBES.contains(mixinClassName)) RecipeIndexProbe.record(mixinClassName.substring(PKG.length()), targetClass);
    }
}
