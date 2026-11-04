package bons.furious.patch.mcreator_pv_c49;

import bons.pure.config.PureMixinPlugin;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * Mixin config plugin of the mcreator_pv_c49 group (mcreator_shared_default_player_variables, since 1.0.49). Everything is
 * Bons and Furious' standard plugin (switch, guard table on LazyOptional.orElse, yields, the log lines), plus, per carrier
 * mixin (one per MCreator mod, PvTargets): the mod must be installed and its PlayerVariables class readable (otherwise the
 * carrier's targets are skipped quietly, before Mixin looks them up); a PlayerVariables constructor of another shape
 * (no constant field at all) makes that mod's carrier stand aside with one WARN. preApply rewrites the accepted read
 * sites of each target class (PvRewrite) before Mixin applies the empty carrier. Same delegation pattern as
 * bons.furious.patch.mcreator_entity_guards.EntityGuardsPlugin.
 */
public final class SharedDefaultsPlugin implements IMixinConfigPlugin {
    private final PureMixinPlugin delegate = new PureMixinPlugin();
    private static final Set<String> LOGGED = ConcurrentHashMap.newKeySet();

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
        PvTargets.Target t = PvTargets.BY_MIXIN.get(mixinClassName);
        if (t == null) return false;
        Boolean listed = modListed(t.modId());
        if (Boolean.FALSE.equals(listed)) return false;                      // the mod is not installed: quietly
        if (!this.delegate.shouldApplyMixin(targetClassName, mixinClassName)) return false;
        Set<String> safe = PvRewrite.safeFields(t.pv());
        if (safe == null) return false;                                      // no PlayerVariables class to read
        if (safe.isEmpty()) {
            if (LOGGED.add(mixinClassName))
                SharedDefaults.LOGGER.warn("Bons and Furious: {} stands aside for {}: its PlayerVariables constructor ({}) is not of the supported shape; its reads allocate as before",
                        SharedDefaults.KEY, t.modId(), t.pv().replace('/', '.'));
            return false;
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
        PvTargets.Target t = PvTargets.BY_MIXIN.get(mixinClassName);
        if (t == null) return;
        Set<String> safe = PvRewrite.safeFields(t.pv());
        if (safe == null || safe.isEmpty()) return;
        int[] r = PvRewrite.rewrite(targetClass, t.pv(), safe);
        SharedDefaults.LOGGER.debug("Bons and Furious: {}: {} read sites of {} share the default in {} ({} other uses keep allocating)",
                SharedDefaults.KEY, r[0], t.pv().replace('/', '.'), targetClassName, r[1]);
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        this.delegate.postApply(targetClassName, targetClass, mixinClassName, mixinInfo);
    }

    /** Whether FML's early mod list lists the mod; null when that list is not available (offline tools). */
    private static Boolean modListed(String modId) {
        try {
            var mods = net.minecraftforge.fml.loading.LoadingModList.get();
            return mods == null ? null : mods.getModFileById(modId) != null;
        } catch (Throwable t) {
            return null;
        }
    }
}
