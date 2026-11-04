package bons.furious.patch.faunify_coo49;

import bons.furious.guard.Guards;
import bons.pure.config.PureMixinPlugin;
import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * Mixin config plugin of the faunify_coo49 group (since 1.0.49). Everything is Bons and Furious' standard plugin
 * (switches, guard table, yields, log lines), plus one choice for faunify_player_proximity_scan_lean: its mouse mixin
 * applies with every accepted build (the tested 1.1.3 and 1.3.3 both have that search), its other four only when the
 * guard matched Faunify 1.3.3 (guard label faunify-1.3.3): the beefly, dragonfly and silk moth flight goals do not exist
 * in 1.1.3 (Mixin would log "Error loading class" for them even as @Pseudo mixins) and the 1.1.3 silk moth's tick has no
 * player search for the wrapper to find. Same delegation pattern as emf_render's EmfRenderPlugin.
 */
public final class FaunifyPlugin implements IMixinConfigPlugin {
    private static final String KEY = "faunify_player_proximity_scan_lean";
    private static final String PREFIX = "bons.furious.mixin.faunify_coo49.";
    private static final Set<String> NEWER_ONLY = Set.of(PREFIX + "PlayerScanBeeflyMixin", PREFIX + "PlayerScanDragonflyMixin",
            PREFIX + "PlayerScanSilkMothFlightMixin", PREFIX + "PlayerScanSilkMothTickMixin");
    /** The accepted 1.3 builds' label prefix in the guard table (patches/faunify_coo49.json sha256_also). */
    private static final String BUILD_13 = "faunify-1.3.";
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
        if (NEWER_ONLY.contains(mixinClassName)) {
            String build = Guards.matchedBuild(KEY);
            return build != null && build.startsWith(BUILD_13);
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
