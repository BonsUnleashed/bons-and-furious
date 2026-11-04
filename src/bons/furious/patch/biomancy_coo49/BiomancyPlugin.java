package bons.furious.patch.biomancy_coo49;

import bons.furious.guard.Guards;
import bons.pure.config.PureMixinPlugin;
import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * Mixin config plugin of the biomancy_coo49 group (since 1.0.49). Everything is Bons and Furious' standard plugin
 * (switches, guard table, yields, log lines), plus one choice for biomancy_spatial_query_memo, whose mixins target the
 * classes each Biomancy line keeps its spatial queries in: SdbiLevelKeyMixin (com.github.elenterius.spatialdb.SDBI,
 * 2.9.8.1-alpha.0, the tested build) and SpatialManagerLevelKeyMixin / SpatialStorageMemoMixin
 * (com.github.elenterius.biomancy.world.spatial, 2.8.19.0, an accepted alternative build, guard label biomancy-2.8.19.0).
 * Only the mixins whose classes the matched build has are offered to Mixin, so Mixin never looks for a class that build
 * lacks (that lookup logs "Error loading class" even for a @Pseudo mixin). Same delegation pattern as emf_render's
 * EmfRenderPlugin.
 */
public final class BiomancyPlugin implements IMixinConfigPlugin {
    private static final String KEY = "biomancy_spatial_query_memo";
    private static final String PKG = "bons.furious.mixin.biomancy_coo49.";
    private static final String SDBI = PKG + "SdbiLevelKeyMixin";
    private static final String OLD_KEY = PKG + "SpatialManagerLevelKeyMixin";
    private static final String OLD_STORE = PKG + "SpatialStorageMemoMixin";
    /** The accepted 2.8 builds' label prefix in the guard table (patches/biomancy_coo49.json sha256_also). */
    private static final String BUILD_28 = "biomancy-2.8.";
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
        if (SDBI.equals(mixinClassName)) return "".equals(Guards.matchedBuild(KEY));
        if (OLD_KEY.equals(mixinClassName) || OLD_STORE.equals(mixinClassName)) {
            String build = Guards.matchedBuild(KEY);
            return build != null && build.startsWith(BUILD_28);
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
