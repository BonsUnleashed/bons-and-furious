package bons.furious.patch.emf_render;

import bons.furious.guard.Guards;
import bons.pure.config.PureMixinPlugin;
import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * Mixin config plugin of the emf_render group (since 1.0.49). Everything is Bons and Furious' standard plugin (switches,
 * guard table, yields, log lines), plus one choice for emf_anger_map_prune, whose two mixins target the class each
 * Entity Model Features family keeps its anger_time code in: EmfAngerMapMixin (EMFAnimationEntityContext, EMF 3.2.x) and
 * EmfMathAngerMapMixin (EMFMath, EMF 3.3.x). The switch's guard accepts the tested 3.2.4 classes or an EMF 3.3 build
 * (alternative fingerprints labelled emf-3.3.10 / emf-3.3.12, in which EMFAnimationEntityContext is absent and EMFMath
 * present; the tested build is the other way round). Only the mixin whose class the matched build has is offered to Mixin,
 * so Mixin never looks for the class that build lacks (that lookup logs "Error loading class" even for a @Pseudo mixin).
 * Every other mixin of the group is decided by the standard plugin alone. Same delegation pattern as
 * mcreator_item_animation's ItemAnimationPlugin.
 */
public final class EmfRenderPlugin implements IMixinConfigPlugin {
    private static final String KEY = "emf_anger_map_prune";
    private static final String ANGER_32 = "bons.furious.mixin.emf_render.EmfAngerMapMixin";
    private static final String ANGER_33 = "bons.furious.mixin.emf_render.EmfMathAngerMapMixin";
    /** The accepted EMF 3.3 builds' label prefix in the guard table (patches/emf_render.json sha256_also). */
    private static final String BUILD_33 = "emf-3.3.";
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
        if (ANGER_32.equals(mixinClassName)) return "".equals(Guards.matchedBuild(KEY));
        if (ANGER_33.equals(mixinClassName)) {
            String build = Guards.matchedBuild(KEY);
            return build != null && build.startsWith(BUILD_33);
        }
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
