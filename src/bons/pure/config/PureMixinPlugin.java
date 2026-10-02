package bons.pure.config;

import bons.furious.guard.Guards;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * The config plugin of every Bons and Furious mixin config. It loads the switches before any game class is
 * transformed (Forge initialises Mixin config plugins first) and decides for each mixin whether it applies:
 *
 *  - the four mixins that predate 1.0.20 are gated by their switch only (MIXIN_KEYS), and since 1.0.27 step aside when
 *    another mod makes the same change (Guards.stepsAside, frame_pacing);
 *  - every other mixin belongs to a guarded switch (bons.furious.guard.Guards): the switch must be enabled and every
 *    method its mixins depend on must match the fingerprint of the tested mod build, otherwise none of that switch's
 *    mixins apply and the target is left exactly as shipped.
 */
public final class PureMixinPlugin implements IMixinConfigPlugin {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    private static final Map<String, String> MIXIN_KEYS = Map.of(
            "agentcraft.pure.mixin.DataCommandsMixin", "vanilla_data_merge_unchanged",
            "agentcraft.terrain.mixin.NoiseChunkMixin", "terrain_density_memo",
            "agentcraft.terrain.mixin.HolderHolderMixin", "terrain_density_memo",
            "bons.pure.pacing.mixin.MinecraftPacingMixin", "frame_pacing");

    @Override
    public void onLoad(String mixinPackage) {
        PureConfig.load();
        Guards.load();
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        String key = MIXIN_KEYS.get(mixinClassName);
        if (key != null) {
            boolean enabled = PureConfig.isEnabled(key);
            if (!enabled) LOGGER.info("Bons and Furious: {} is disabled by config; {} is not applied to {}", key, mixinClassName, targetClassName);
            return enabled && !Guards.stepsAside(key, targetClassName);
        }
        if (Guards.knows(mixinClassName)) return Guards.shouldApply(mixinClassName, targetClassName);
        return true; // mixins without a switch (TrackworkResources) always apply
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
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        String key = Guards.keyOf(mixinClassName);
        if (key != null) LOGGER.debug("Bons and Furious: {} applied to {}", key, targetClassName);
    }
}
