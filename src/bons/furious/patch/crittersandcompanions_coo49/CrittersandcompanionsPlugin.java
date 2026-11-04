package bons.furious.patch.crittersandcompanions_coo49;

import bons.furious.guard.Guards;
import bons.pure.config.PureMixinPlugin;
import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * Mixin config plugin of the crittersandcompanions_coo49 group (since 1.0.49). Everything is Bons and Furious' standard
 * plugin (switches, guard table, yields, log lines), plus one choice for crittersandcompanions_necklace_loop,
 * crittersandcompanions_leash_render_empty_skip and crittersandcompanions_food_scan_interval, whose mixins come per package
 * Critters and Companions has used: the *EterdeltaMixin (com.github.eterdelta, 2.3.5, the tested build) and the *BonsaiMixin (io.github.bonsaistudi0s,
 * 2.7.1, an accepted alternative build, guard label crittersandcompanions-2.7.1); crittersandcompanions_behaviour_fast_path
 * has only a 2.7.1 mixin (2.3.5 has no Behaviours class). Only the mixin whose class the matched
 * build has is offered to Mixin, so Mixin never looks for the class that build lacks (that lookup logs "Error loading
 * class" even for a @Pseudo mixin). Same delegation pattern as emf_render's EmfRenderPlugin.
 */
public final class CrittersandcompanionsPlugin implements IMixinConfigPlugin {
    private static final String PKG = "bons.furious.mixin.crittersandcompanions_coo49.";
    /** Per build-specific mixin: its switch key and whether it is the 2.3.5 (eterdelta) one. */
    private static final java.util.Map<String, String[]> BUILD_MIXINS = java.util.Map.of(
            PKG + "NecklaceLoopEterdeltaMixin", new String[] {"crittersandcompanions_necklace_loop", "eterdelta"},
            PKG + "NecklaceLoopBonsaiMixin", new String[] {"crittersandcompanions_necklace_loop", "bonsai"},
            PKG + "LeashEmptyEterdeltaMixin", new String[] {"crittersandcompanions_leash_render_empty_skip", "eterdelta"},
            PKG + "LeashEmptyBonsaiMixin", new String[] {"crittersandcompanions_leash_render_empty_skip", "bonsai"},
            PKG + "FoodScanEterdeltaMixin", new String[] {"crittersandcompanions_food_scan_interval", "eterdelta"},
            PKG + "FoodScanBonsaiOtterMixin", new String[] {"crittersandcompanions_food_scan_interval", "bonsai"},
            PKG + "FoodScanBonsaiLeafMixin", new String[] {"crittersandcompanions_food_scan_interval", "bonsai"},
            PKG + "BehaviourFastPathBonsaiMixin", new String[] {"crittersandcompanions_behaviour_fast_path", "bonsai"});
    /** The accepted 2.x builds' label prefix in the guard table (patches/crittersandcompanions_coo49.json sha256_also). */
    private static final String BUILD_BONSAI = "crittersandcompanions-2.";
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
        String[] km = BUILD_MIXINS.get(mixinClassName);
        if (km == null) return true;
        String build = Guards.matchedBuild(km[0]);
        return km[1].equals("eterdelta") ? "".equals(build) : build != null && build.startsWith(BUILD_BONSAI);
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
