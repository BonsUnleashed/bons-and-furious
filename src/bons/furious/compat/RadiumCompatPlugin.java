package bons.furious.compat;

import bons.furious.guard.Guards;
import bons.pure.config.PureConfig;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * Stand-ins for Radium options that cannot run in this pack.
 *
 *  - vanilla_block_entity_tick_state: Radium's mixin.world.block_entity_ticking.support_cache, off in Radium Re-Reforged
 *    because its LevelChunk mixin cannot apply on Forge. Decided by its guards alone; while it applies, Radium's three
 *    support_cache mixins are cancelled (bons_and_furious.guards.tsv "cancel" lines), so the two never meet even if
 *    someone switches Radium's option on.
 *  - vanilla_poi_chunk_sections: a cheaper vanilla getInChunk while Radium's mixin.ai.poi is off (Valkyrien Skies forces
 *    it off). Applied only when Radium's own ai.poi mixin will not be: that one replaces the same method with an index.
 *
 * Radium decides its mixins from options it builds while its config plugin loads, before any config is prepared, so they
 * are final when this config is prepared.
 */
public final class RadiumCompatPlugin implements IMixinConfigPlugin {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    static final String POI_MIXIN = "bons.furious.mixin.radium_compat.PoiChunkSectionsMixin";
    static final String POI_KEY = "vanilla_poi_chunk_sections";
    static final String RADIUM_POI_MIXIN = "ai.poi.PointOfInterestStorageMixin";
    private static final Map<String, Boolean> ALLOW = new ConcurrentHashMap<>();

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
        if (!Guards.knows(mixinClassName)) return false;
        if (mixinClassName.equals(POI_MIXIN) && !ALLOW.computeIfAbsent(POI_KEY, k -> radiumPoiOff())) return false;
        return Guards.shouldApply(mixinClassName, targetClassName);
    }

    /** True when Radium is absent or will not apply its ai.poi PointOfInterestStorageMixin. */
    static boolean radiumPoiOff() {
        try {
            Object config = radiumConfig(RadiumCompatPlugin.class.getClassLoader());
            if (config == null) return true;
            Object option = config.getClass().getMethod("getEffectiveOptionForMixin", String.class).invoke(config, RADIUM_POI_MIXIN);
            if (option != null && (Boolean) option.getClass().getMethod("isEnabled").invoke(option)) {
                LOGGER.info("Bons and Furious: {} leaves PoiManager to Radium: Radium's own mixin.ai.poi applies", POI_KEY);
                return false;
            }
            return true;
        } catch (Throwable t) {
            LOGGER.warn("Bons and Furious: {} could not check Radium's mixin.ai.poi ({}); PoiManager is left unchanged", POI_KEY, t.toString());
            return false;
        }
    }

    /** Radium's live CaffeineConfig (the options its mixin plugin reads), or null when Radium is absent or not loaded. */
    private static Object radiumConfig(ClassLoader loader) throws ReflectiveOperationException {
        Class<?> mod;
        try {
            mod = Class.forName("me.jellysquid.mods.lithium.common.LithiumMod", true, loader);
        } catch (ClassNotFoundException e) {
            return null;
        }
        Object plugin = mod.getField("CONFIG").get(null);
        if (plugin == null) return null;
        Field f = Class.forName("net.caffeinemc.caffeineconfig.AbstractCaffeineConfigMixinPlugin", false, loader).getDeclaredField("config");
        f.setAccessible(true);
        return f.get(plugin);
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
