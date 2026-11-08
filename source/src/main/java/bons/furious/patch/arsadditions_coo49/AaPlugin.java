package bons.furious.patch.arsadditions_coo49;

import bons.pure.config.PureMixinPlugin;
import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * Mixin config plugin of the arsadditions_coo49 group (since 1.0.49). Everything is Bons and Furious' standard plugin
 * (switches, guard table, yields, log lines); in addition, after the far-burst switch's probe mixins have been applied to
 * LevelRenderer and ClientLevel (priority 2,000,000, so every other mod's mixin on those classes is already there),
 * AaParticleProbe records whether their particle path is the game's own. Same delegation pattern as the
 * mcreator_entity_guards group's EntityGuardsPlugin.
 */
public final class AaPlugin implements IMixinConfigPlugin {
    private static final String RENDERER_PROBE = "bons.furious.mixin.arsadditions_coo49.AaLevelRendererProbeMixin";
    private static final String LEVEL_PROBE = "bons.furious.mixin.arsadditions_coo49.AaClientLevelProbeMixin";
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
        if (RENDERER_PROBE.equals(mixinClassName)) AaParticleProbe.recordLevelRenderer(targetClass);
        else if (LEVEL_PROBE.equals(mixinClassName)) AaParticleProbe.recordClientLevel(targetClass);
    }
}
