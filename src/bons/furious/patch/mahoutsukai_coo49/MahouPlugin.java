package bons.furious.patch.mahoutsukai_coo49;

import bons.pure.config.PureMixinPlugin;
import java.util.List;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * Mixin config plugin of the mahoutsukai_coo49 group (since 1.0.49). Everything is Bons and Furious' standard plugin
 * (switches, guard table, yields, log lines); in addition, after each of mahoutsukai_projector_enchant_precheck's five probe
 * mixins has been applied (priority 3000, so every other mod's mixin on the class is already there), MahouProjectorProbe
 * records whether the methods the precheck relies on are still as shipped; when one has already refused by the time Mahou
 * Tsukai's RenderProjectorEnchant is transformed, the precheck's redirect is left out of it altogether. Same delegation
 * pattern as vanilla_cooking_recipe_index's RecipeIndexPlugin.
 */
public final class MahouPlugin implements IMixinConfigPlugin {
    private static final String PKG = "bons.furious.mixin.mahoutsukai_coo49.";
    private static final Set<String> PROBES = Set.of(PKG + "MahouEffectUtilProbeMixin", PKG + "MahouEnchantmentHelperProbeMixin",
            PKG + "MahouItemStackProbeMixin", PKG + "MahouForgeItemStackProbeMixin", PKG + "MahouForgeItemProbeMixin");
    private static final String PRECHECK = PKG + "MahouProjectorPrecheckMixin";
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
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
        boolean apply = this.delegate.shouldApplyMixin(targetClassName, mixinClassName);
        // a probe that refused before Mahou Tsukai's renderer is transformed (the chain's vanilla and Forge classes load at
        // bootstrap): the renderer keeps no precheck at all, so its stock call costs nothing extra
        if (apply && PRECHECK.equals(mixinClassName) && MahouProjectorProbe.anyRefused()) {
            LOGGER.warn("Bons and Furious: mahoutsukai_projector_enchant_precheck stands aside ({}); RenderProjectorEnchant is left unchanged by it",
                    MahouProjectorProbe.why());
            return false;
        }
        return apply;
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
        if (PROBES.contains(mixinClassName)) MahouProjectorProbe.record(mixinClassName.substring(PKG.length()), targetClass);
    }
}
