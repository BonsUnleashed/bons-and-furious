package bons.pure.config;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * Loads the early configuration before any game class is transformed and applies the
 * switches to this mod's own mixins. Forge initialises Mixin config plugins before the
 * game's main class is loaded, which is before any coremod transformer runs for a mod class.
 */
public final class PureMixinPlugin implements IMixinConfigPlugin {
    private static final Logger LOGGER = LogManager.getLogger("Bons Pure Optimizations");
    private static final Map<String, String> MIXIN_KEYS = Map.of(
            "agentcraft.pure.mixin.DataCommandsMixin", "vanilla_data_merge_unchanged",
            "agentcraft.terrain.mixin.NoiseChunkMixin", "terrain_density_memo",
            "agentcraft.terrain.mixin.HolderHolderMixin", "terrain_density_memo",
            "bons.pure.pacing.mixin.MinecraftPacingMixin", "frame_pacing");

    @Override
    public void onLoad(String mixinPackage) {
        PureConfig.load();
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        String key = MIXIN_KEYS.get(mixinClassName);
        if (key == null) return true; // shared API accessors always apply
        boolean enabled = PureConfig.isEnabled(key);
        if (!enabled) LOGGER.info("Bons Pure Optimizations: {} is disabled by config; {} is not applied to {}", key, mixinClassName, targetClassName);
        return enabled;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}
