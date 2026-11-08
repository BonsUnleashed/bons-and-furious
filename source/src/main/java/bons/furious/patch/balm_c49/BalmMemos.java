package bons.furious.patch.balm_c49;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import net.blay09.mods.balm.api.network.ConfigReflection;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switches balm_config_identifier_memo and balm_dynamic_model_state_key (Balm 21.0.67 for NeoForge 1.21.1,
 * All Rights Reserved: injectors only, no Balm code carried). Ours.
 *
 * balm_config_identifier_memo (both sides). What Balm does: every config read of every Balm mod through
 * Balm.getConfig().getActiveConfig(Class) (also the deprecated getActive(Class), which forwards to it, e.g. Cooking for
 * Blockheads 21.1's CookingForBlockheadsConfig.getActive) asks ConfigReflection.getIdentifier(class) (one or two annotation
 * lookups and a new, validated ResourceLocation), allocates a lambda, and only then looks in activeReflectionConfigs, which
 * almost always holds the answer. What the switch does: the identifier comes from a ClassValue (computed once per class by
 * Balm's own getIdentifier; a class without the annotation throws Balm's exception again on every call, nothing is cached
 * for it), and the map is asked with get first; only when it holds nothing does Balm's method run (computeIfAbsent, as
 * shipped). Why identical: the identifier is a pure function of the class's annotations (equal value; it is only used as
 * the map key); a present value is exactly what computeIfAbsent returns without calling its function (the map is a
 * ConcurrentHashMap, it holds no nulls); removals by saveLocalConfig (also through NeoForgeBalmConfig's override, which
 * calls it first) / setActiveConfig / resetToLocalConfig are honoured because the map is still asked every time.
 *
 * balm_dynamic_model_state_key (client). What Balm does: AbstractCachedDynamicModel.getQuads (the base of
 * NeoForgeCachedDynamicModel, which Balm's loadDynamicModel builds) makes the model cache key with BlockState.toString() on
 * every quad request (a StringBuilder over the block's id and every property). What the switch does: the string is
 * remembered per BlockState object (a state's toString never changes once the block is registered: Block{id} + its fixed
 * property values) and handed out again; the cache key is the same text, so the model cache finds the same entry.
 *
 * Flags: -Dbons_and_furious.balmConfigIdentifierMemo=false, -Dbons_and_furious.balmDynamicModelStateKey=false.
 * Shadow (.shadow=true on either): the original runs too and the answers are compared (SHADOW_CHECKS / SHADOW_MISMATCHES,
 * WARN for the first 20).
 */
public final class BalmMemos {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean configEnabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.balmConfigIdentifierMemo", "true"));
    public static final boolean CONFIG_SHADOW = Boolean.getBoolean("bons_and_furious.balmConfigIdentifierMemo.shadow");
    public static volatile boolean modelEnabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.balmDynamicModelStateKey", "true"));
    public static final boolean MODEL_SHADOW = Boolean.getBoolean("bons_and_furious.balmDynamicModelStateKey.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    private static volatile boolean configAnnounced, modelAnnounced;

    private BalmMemos() {
    }

    private static final ClassValue<ResourceLocation> IDENTIFIERS = new ClassValue<>() {
        @Override
        protected ResourceLocation computeValue(Class<?> type) {
            return ConfigReflection.getIdentifier(type);   // throws Balm's own exception for a class without @Config (not cached)
        }
    };

    private static final Map<BlockState, String> STATE_KEYS = new ConcurrentHashMap<>();

    /** In place of AbstractBalmConfig.getActiveConfig(Class) (MixinExtras @WrapMethod). */
    public static Object activeConfig(Map<ResourceLocation, Object> active, Class<?> type, Operation<Object> original) {
        if (!configEnabled) return original.call(type);
        Object v = active.get(IDENTIFIERS.get(type));
        if (v == null) return original.call(type);
        if (!configAnnounced) {
            configAnnounced = true;
            LOGGER.info("Bons and Furious: balm_config_identifier_memo applies (Balm config reads skip the identifier rebuild){}", CONFIG_SHADOW ? " - shadow verification on" : "");
        }
        if (CONFIG_SHADOW) {
            Object stock = original.call(type);
            SHADOW_CHECKS.incrementAndGet();
            if (stock != v) mismatch("config " + type.getName(), stock, v);
        }
        return v;
    }

    /** In place of BlockState.toString() in AbstractCachedDynamicModel.getQuads (MixinExtras @WrapOperation). */
    public static String stateKey(BlockState state, Operation<String> original) {
        if (!modelEnabled) return original.call(state);
        String s = STATE_KEYS.get(state);
        if (s == null) {
            s = original.call(state);
            String raced = STATE_KEYS.putIfAbsent(state, s);
            if (raced != null) s = raced;
            if (!modelAnnounced) {
                modelAnnounced = true;
                LOGGER.info("Bons and Furious: balm_dynamic_model_state_key applies (Balm's dynamic models reuse each block state's cache key){}", MODEL_SHADOW ? " - shadow verification on" : "");
            }
        } else if (MODEL_SHADOW) {
            String stock = original.call(state);
            SHADOW_CHECKS.incrementAndGet();
            if (!stock.equals(s)) mismatch("state key", stock, s);
        }
        return s;
    }

    private static void mismatch(String what, Object stock, Object ours) {
        long m = SHADOW_MISMATCHES.incrementAndGet();
        if (m <= 20) LOGGER.warn("Bons and Furious: Balm memo shadow mismatch #{} ({}): Balm {} vs memo {}", m, what, stock, ours);
    }

    /** For the harness. */
    public static int stateKeys() {
        return STATE_KEYS.size();
    }
}
