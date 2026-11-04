package bons.furious.patch.curios_presence;

import bons.furious.mixin.curios_presence.CapabilityDispatcherCuriosLookupAccessor;
import bons.furious.mixin.curios_presence.CapabilityProviderCuriosLookupAccessor;
import bons.furious.mixin.curios_presence.CuriosProviderLookupAccessor;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityDispatcher;
import net.minecraftforge.common.capabilities.CapabilityProvider;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;
import top.theillusivec4.curios.common.capability.CurioInventoryCapability;

/**
 * Bons and Furious switch curios_inventory_lookup_layer (Curios API 5.14.1+1.20.1, LGPL-3.0; Forge 47.4.28; both sides;
 * since 1.0.49). Ours entirely: no Curios code is carried. Idea: Collections Of Optimizations (terracurio.cachedCurioLookup,
 * l2backpack.cacheBackLayerScan), idea text only; the design is ours and keeps no cache of curio contents.
 *
 * Every mod asks Curios for an entity's inventory through CuriosApi.getCuriosInventory, which Curios' own mixin sends to
 * CuriosImplMixinHooks.getCuriosInventory(entity) = entity == null ? empty : entity.getCapability(CuriosCapability.INVENTORY):
 * a walk over every capability provider attached to the entity (dozens in a large pack). Curios' own provider answers
 * empty while CuriosApi.getEntitySlots(entity) is empty (every entity type no datapack gives slots to) and otherwise its
 * own final LazyOptional field. CuriosImplHooksLookupMixin wraps that method and answers from the entity's kept record:
 *  - a type without slots (the same CuriosApi.getEntitySlots question Curios' provider asks, asked on every call): when a
 *    full lookup with this same capability dispatcher already came back LazyOptional.empty() while the capabilities were
 *    valid, the answer is LazyOptional.empty() without the walk (the CuriosSlotlessTick logic of curios_slotless_tick_skip,
 *    which only served CuriosEventHandler.tick, here at the API entry for every caller);
 *  - a type with slots: when a full lookup with this dispatcher returned exactly Curios' own provider's LazyOptional (the
 *    provider of this entity found in the dispatcher's provider array), that same object is handed out again while the
 *    capabilities are valid, the dispatcher is the same object and the optional is still present.
 * Every other case runs the original lookup (and learns from it). Only entity classes that do not override getCapability
 * are learned (LivingEntity's, Player's and AbstractHorse's own ITEM_HANDLER branch excepted, as in CallerCapabilityHandles).
 *
 * Why the answer is identical: Forge's CapabilityDispatcher answers the first present answer of its final provider array,
 * else LazyOptional.empty() (ModernFix's generated dispatcher walks the same array in the same order); an invalid provider
 * answers LazyOptional.empty() without asking anyone. Providers answer as a function of the Capability object (the premise
 * of every capability cache, ModernFix's faster_capabilities included): so for the same dispatcher a provider that answered
 * empty, or a LazyOptional that is not present, answers the same again (a LazyOptional never becomes present again), and
 * Curios' provider answers its final optional exactly while the slot map is non-empty, which is checked on every call. A
 * datapack reload that gives the type slots (or takes them) therefore switches between the two records at once, and an
 * entity on which another mod's provider answers the inventory capability first is never answered from a record.
 *
 * -Dbons_and_furious.curiosInventoryLookupLayer=false asks the entity every time. -Dbons_and_furious.curiosInventoryLookupLayer
 * .shadow=true (verification runs only) runs the original lookup on every answer the record would give, returns the
 * original's answer and counts non-identical answers in SHADOW_CHECKS / SHADOW_MISMATCHES (WARN for the first 20).
 */
public final class CuriosInventoryLookup {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.curiosInventoryLookupLayer", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.curiosInventoryLookupLayer.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    /** Lookups answered from a record (statistics for probes; plain fields, racy). */
    public static long keptAnswers, emptyAnswers;
    /** The layer's mixins are in the game: the per-entity field and the three accessors. */
    static final boolean READY = CuriosLookupState.class.isAssignableFrom(LivingEntity.class)
            && CapabilityProviderCuriosLookupAccessor.class.isAssignableFrom(CapabilityProvider.class)
            && CapabilityDispatcherCuriosLookupAccessor.class.isAssignableFrom(CapabilityDispatcher.class)
            && CuriosProviderLookupAccessor.class.isAssignableFrom(CurioInventoryCapability.Provider.class);
    private static volatile boolean announced;

    private CuriosInventoryLookup() {
    }

    /** One entity's kept answer; immutable (published through a plain field: final-field semantics). */
    static final class Kept {
        final CapabilityDispatcher dispatcher;
        /** Curios' own provider's LazyOptional, or null: this dispatcher answered LazyOptional.empty() while slotless. */
        final LazyOptional<ICuriosItemHandler> optional;

        Kept(CapabilityDispatcher dispatcher, LazyOptional<ICuriosItemHandler> optional) {
            this.dispatcher = dispatcher;
            this.optional = optional;
        }
    }

    /** True for entity classes whose getCapability is Forge's own LivingEntity/Player/AbstractHorse code (ITEM_HANDLER only). */
    static final ClassValue<Boolean> PLAIN = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            for (Class<?> k = type; k != null && k != LivingEntity.class; k = k.getSuperclass()) {
                if (k == Player.class || k == AbstractHorse.class) continue;
                if (declaresGetCapability(k)) return Boolean.FALSE;
                if (k == Object.class) return Boolean.FALSE;
            }
            return Boolean.TRUE;
        }
    };

    private static boolean declaresGetCapability(Class<?> k) {
        try {
            for (java.lang.reflect.Method m : k.getDeclaredMethods()) {
                if (!m.getName().equals("getCapability")) continue;
                Class<?>[] p = m.getParameterTypes();
                if (p.length >= 1 && p[0] == Capability.class && (p.length == 1 || p.length == 2 && p[1] == Direction.class)) return true;
            }
            return false;
        } catch (Throwable t) {
            return true;
        }
    }

    /** CuriosImplMixinHooks.getCuriosInventory(entity), wrapped. */
    public static LazyOptional<ICuriosItemHandler> inventory(LivingEntity entity, Operation<LazyOptional<ICuriosItemHandler>> original) {
        if (!enabled || !READY || entity == null || entity.m_9236_() == null) return original.call(entity);
        CapabilityProviderCuriosLookupAccessor caps = (CapabilityProviderCuriosLookupAccessor) entity;
        CapabilityDispatcher dispatcher = caps.bons$lookupDispatcher();
        if (dispatcher == null) return original.call(entity);
        boolean slotless = CuriosApi.getEntitySlots(entity).isEmpty();
        CuriosLookupState state = (CuriosLookupState) entity;
        if (state.bons$curiosLookup() instanceof Kept kept && kept.dispatcher == dispatcher) {
            if (slotless) {
                if (kept.optional == null) {
                    if (SHADOW) return shadow(entity, LazyOptional.empty(), original);
                    emptyAnswers++;
                    return LazyOptional.empty();
                }
            } else if (kept.optional != null && caps.bons$lookupCapsValid() && kept.optional.isPresent()) {
                if (SHADOW) return shadow(entity, kept.optional, original);
                keptAnswers++;
                return kept.optional;
            }
        }
        LazyOptional<ICuriosItemHandler> answer = original.call(entity);
        if (caps.bons$lookupCapsValid() && PLAIN.get(entity.getClass())) {
            if (slotless) {
                if ((Object) answer == LazyOptional.empty()) keep(state, new Kept(dispatcher, null));
            } else if (answer.isPresent() && answer == curiosOptional(dispatcher, entity)) {
                keep(state, new Kept(dispatcher, answer));
            }
        }
        return answer;
    }

    /**
     * A hint for callers that only skip work for entities WITHOUT an inventory (curios_render_layer_empty_skip): true when
     * the entity's record holds Curios' own inventory handle (a slotted entity), so such a caller runs the shipped code at
     * once instead of asking first. Never used as an answer: false means "ask", not "no inventory".
     */
    static boolean keptInventory(LivingEntity entity) {
        return ((CuriosLookupState) entity).bons$curiosLookup() instanceof Kept kept && kept.optional != null;
    }

    /** Curios' own inventory provider of this entity in the dispatcher's provider array: its final LazyOptional, else null. */
    static Object curiosOptional(CapabilityDispatcher dispatcher, LivingEntity entity) {
        // CapabilityDispatcher is final: the accessor interface is only there at run time (the mixin), hence the Object cast
        ICapabilityProvider[] providers = ((CapabilityDispatcherCuriosLookupAccessor) (Object) dispatcher).bons$lookupProviders();
        if (providers == null) return null;
        for (ICapabilityProvider p : providers) {
            if (p != null && p.getClass() == CurioInventoryCapability.Provider.class) {
                CuriosProviderLookupAccessor curios = (CuriosProviderLookupAccessor) p;
                if (curios.bons$lookupWearer() == entity) return curios.bons$lookupOptional();
            }
        }
        return null;
    }

    private static void keep(CuriosLookupState state, Kept kept) {
        state.bons$setCuriosLookup(kept);
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: curios_inventory_lookup_layer applies (Curios inventory lookups reuse each entity's kept answer instead of walking its capability providers){}",
                    SHADOW ? " - shadow verification on" : "");
        }
    }

    private static LazyOptional<ICuriosItemHandler> shadow(LivingEntity entity, LazyOptional<ICuriosItemHandler> ours,
                                                           Operation<LazyOptional<ICuriosItemHandler>> original) {
        LazyOptional<ICuriosItemHandler> asked = original.call(entity);
        SHADOW_CHECKS.incrementAndGet();
        if (asked != ours && SHADOW_MISMATCHES.incrementAndGet() <= 20)
            LOGGER.warn("Bons and Furious: curios_inventory_lookup_layer shadow mismatch on {}: kept {}, asked {}", entity,
                    ours.isPresent() ? "the inventory" : "empty", asked.isPresent() ? "the inventory" : "empty");
        return asked;
    }
}
