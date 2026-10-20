package bons.furious.patch.mowziesmobs;

import bons.furious.mixin.mowziesmobs.CapabilityProviderStateAccessor;
import com.bobmowzie.mowziesmobs.server.capability.CapabilityHandler;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityDispatcher;
import net.minecraftforge.common.util.LazyOptional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch mowziesmobs_capability_handles (Mowzie's Mobs 1.7.3, both sides). No Mowzie's Mobs code here.
 *
 * Mowzie's CapabilityHandler.getCapability(entity, capability) asked the entity's capability dispatcher twice per call
 * (entity.getCapability(capability).isPresent(), then entity.getCapability(capability).orElseThrow(...)), and every
 * living entity calls it three times per tick on both sides (ServerEventHandler.onLivingTick: frozen, living, ability),
 * the client again per rendered entity per frame (renderLivingEvent, onRenderLiving, the frozen and sunblock layers).
 * Each ask walks the entity's capability providers until one answers.
 *
 * Handle form (1.0.28). CapabilityHandlerLookupMixin sends both asks here (lookup). For a LivingEntity and one of
 * Mowzie's four capabilities (FROZEN, LIVING, ABILITY, PLAYER) the first present answer is kept on the entity together
 * with the dispatcher it came from (since 1.0.46 only for an entity whose class answers getCapability with Forge's own
 * LivingEntity, Player or AbstractHorse code, PLAIN below, the rule cataclysm_capability_handles has had since 1.0.36: a
 * class with its own getCapability may answer by its own state, which a kept answer would not see; the 1.0.46 proof's
 * test entity that hides its capabilities showed 1.0.28's form handing out the kept answer there, and no class in the
 * pack does that for Mowzie's capabilities, so the rule changes no answer in game), and handed out again while (1) the
 * entity's capabilities are valid
 * (CapabilityProvider.valid: an invalidated provider answers empty without asking anyone, so the original path runs),
 * (2) the entity still has the same dispatcher object and (3) the kept LazyOptional is still present
 * (LazyOptional.isPresent: not invalidated). Any other entity, capability or state runs the original ask.
 *
 * Why the answer is identical: Forge's CapabilityDispatcher.caps is a final array built once in the entity's
 * constructor and the dispatcher returns the first present answer in that order; each Mowzie provider answers its own
 * capability with this.instance.cast(), the same final LazyOptional object every time (Capability.orEmpty), which Mowzie
 * never invalidates. So the dispatcher hands out the identical object on every ask as long as the providers before
 * Mowzie's answer a Mowzie capability the same way every time, the assumption every capability cache (ModernFix's
 * faster_capabilities included) rests on: providers answer as a function of the Capability object. Check (3) also
 * covers someone invalidating Mowzie's LazyOptional: the original walk then runs and finds the next provider, as before.
 *
 * Value form (since 1.0.46; -Dbons_and_furious.mowziesmobsCapabilityHandles.values=false keeps the handle form only).
 * The kept entry also holds the value orElseThrow returned for that LazyOptional (CapabilityHandlerValueMixin keeps it
 * at the original call), and CapabilityHandlerValueMixin answers the whole getCapability(entity, capability) from it
 * while (1)-(3) hold: Mowzie's own null and isRemoved checks still run, then the kept value is returned without the two
 * dispatcher asks, isPresent and orElseThrow. A LazyOptional resolves its value once and never changes it (Forge's
 * LazyOptional.getValue keeps the first result; isValid only ever goes from true to false), so while the kept
 * LazyOptional is present its orElseThrow returns the kept value. The other entry is "absent": a dispatcher ask that
 * found no present answer while the capabilities were valid, for an entity whose class answers getCapability with
 * Forge's own LivingEntity, Player or AbstractHorse code (PLAIN; those answer only ITEM_HANDLER themselves and pass
 * every other capability to the dispatcher). Under the same premise every provider of that dispatcher answers the
 * capability with the same LazyOptional on every ask, and none of those is present; a LazyOptional never becomes
 * present again, so the dispatcher answers LazyOptional.empty() for as long as the entity keeps it (valid or not), and
 * Mowzie's getCapability returns null. That is ABILITY and PLAYER on every living entity that is not a player: one
 * dispatcher walk per mob per tick before, none now.
 *
 * The table is one Object[] per living entity, replaced (never changed in place) on every fill and published through a
 * volatile field, so a reader on another thread sees whole entries: [0] the dispatcher, then per capability the
 * LazyOptional (or ABSENT) and the value (or null until orElseThrow returned it).
 *
 * -Dbons_and_furious.mowziesmobsCapabilityHandles.shadow=true (verification runs only) also asks the dispatcher whenever
 * a kept handle or value is handed out and counts any non-identical answer in SHADOW_CHECKS / SHADOW_MISMATCHES (the
 * original answer is then returned).
 */
public final class MowzieCapabilityHandles {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    /** Runtime switch (the config switch acts when classes are transformed). -Dbons_and_furious.mowziesmobsCapabilityHandles=false asks the dispatcher every time. */
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.mowziesmobsCapabilityHandles", "true"));
    /** Since 1.0.46: the value form and the absent entries (false = the 1.0.28 handle form only). */
    public static volatile boolean values = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.mowziesmobsCapabilityHandles.values", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.mowziesmobsCapabilityHandles.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    /** The table entry of a capability no provider of the entity answers; also kept()'s answer for it. */
    public static final Object ABSENT = new Object() {
        @Override
        public String toString() {
            return "bons_and_furious:absent";
        }
    };
    /** kept()'s answer when nothing is kept: run the original. */
    public static final Object MISS = new Object() {
        @Override
        public String toString() {
            return "bons_and_furious:miss";
        }
    };
    private static final int SLOTS = 4;
    private static volatile boolean announced;

    private MowzieCapabilityHandles() {
    }

    private static int slot(Capability<?> cap) {
        if (cap == CapabilityHandler.FROZEN_CAPABILITY) return 0;
        if (cap == CapabilityHandler.LIVING_CAPABILITY) return 1;
        if (cap == CapabilityHandler.ABILITY_CAPABILITY) return 2;
        if (cap == CapabilityHandler.PLAYER_CAPABILITY) return 3;
        return -1;
    }

    /**
     * True when getCapability of every class between the entity's class and LivingEntity is Forge's own (LivingEntity,
     * Player, AbstractHorse: ITEM_HANDLER only, then the dispatcher). Absent entries are kept for these classes only.
     */
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
            return true;   // methods naming a missing class: treat as not plain
        }
    }

    /** entity.getCapability(cap), answered from the entity's kept entry when that is the same answer (both dispatcher asks). */
    @SuppressWarnings("unchecked")
    public static <T> LazyOptional<T> lookup(Entity entity, Capability<T> cap) {
        if (!enabled || !(entity instanceof LivingEntity)) return entity.getCapability(cap);
        int slot = slot(cap);
        if (slot < 0) return entity.getCapability(cap);
        CapabilityProviderStateAccessor state = (CapabilityProviderStateAccessor) entity;
        MowzieHandleHolder holder = (MowzieHandleHolder) entity;
        boolean valid = state.bons$mowzieCapsValid();
        CapabilityDispatcher dispatcher = state.bons$mowzieCapDispatcher();
        Object[] table = (Object[]) holder.bons$mowzieHandles();
        if (table != null && dispatcher != null && table[0] == dispatcher) {
            Object kept = table[1 + 2 * slot];
            if (kept == ABSENT) {
                if (values) {
                    LazyOptional<T> empty = LazyOptional.empty();
                    return SHADOW ? shadow(entity, cap, empty) : empty;
                }
            } else if (kept != null && valid && ((LazyOptional<?>) kept).isPresent()) {
                return SHADOW ? shadow(entity, cap, (LazyOptional<T>) kept) : (LazyOptional<T>) kept;
            }
        }
        LazyOptional<T> answer = entity.getCapability(cap);
        if (valid && dispatcher != null && PLAIN.get(entity.getClass())) {
            if (answer.isPresent()) keep(holder, slot, dispatcher, answer, null);
            else if (values && answer == LazyOptional.empty()) keep(holder, slot, dispatcher, ABSENT, null);
        }
        return answer;
    }

    /**
     * For CapabilityHandlerValueMixin: the value Mowzie's getCapability would return from its dispatcher asks, ABSENT when
     * those find nothing, MISS when nothing is kept (the original runs). Reads fields only; calls no entity method.
     */
    public static Object kept(Entity entity, Capability<?> cap) {
        if (!enabled || !values || !(entity instanceof LivingEntity)) return MISS;
        int slot = slot(cap);
        if (slot < 0) return MISS;
        Object[] table = (Object[]) ((MowzieHandleHolder) entity).bons$mowzieHandles();
        if (table == null) return MISS;
        CapabilityProviderStateAccessor state = (CapabilityProviderStateAccessor) entity;
        CapabilityDispatcher dispatcher = state.bons$mowzieCapDispatcher();
        if (dispatcher == null || table[0] != dispatcher) return MISS;
        Object kept = table[1 + 2 * slot];
        if (kept == ABSENT) return ABSENT;
        Object value = table[2 + 2 * slot];
        if (value == null || !state.bons$mowzieCapsValid() || !((LazyOptional<?>) kept).isPresent()) return MISS;
        return value;
    }

    /** At the original orElseThrow: keeps its value next to the kept LazyOptional it was asked of. */
    public static void keepValue(Entity entity, Capability<?> cap, LazyOptional<?> optional, Object value) {
        if (!enabled || !values || value == null || !(entity instanceof LivingEntity)) return;
        int slot = slot(cap);
        if (slot < 0) return;
        MowzieHandleHolder holder = (MowzieHandleHolder) entity;
        Object[] table = (Object[]) holder.bons$mowzieHandles();
        if (table == null || table[1 + 2 * slot] != optional || table[2 + 2 * slot] == value) return;
        if (table[0] != ((CapabilityProviderStateAccessor) entity).bons$mowzieCapDispatcher()) return;
        Object[] next = table.clone();
        next[2 + 2 * slot] = value;
        holder.bons$setMowzieHandles(next);
    }

    /** Shadow check of a value or absent answer handed out by CapabilityHandlerValueMixin: the original's result wins. */
    public static Object shadowValue(Entity entity, Capability<?> cap, Object ours, Object asked) {
        SHADOW_CHECKS.incrementAndGet();
        if (asked != ours && SHADOW_MISMATCHES.incrementAndGet() <= 20)
            LOGGER.warn("Bons and Furious: mowziesmobs_capability_handles shadow mismatch (value) on {} for {}", entity, cap.getName());
        return asked;
    }

    private static <T> LazyOptional<T> shadow(Entity entity, Capability<T> cap, LazyOptional<T> kept) {
        LazyOptional<T> asked = entity.getCapability(cap);
        SHADOW_CHECKS.incrementAndGet();
        if (asked != kept && SHADOW_MISMATCHES.incrementAndGet() <= 20)
            LOGGER.warn("Bons and Furious: mowziesmobs_capability_handles shadow mismatch on {} for {}", entity, cap.getName());
        return asked;
    }

    private static void keep(MowzieHandleHolder holder, int slot, CapabilityDispatcher dispatcher, Object optional, Object value) {
        Object[] old = (Object[]) holder.bons$mowzieHandles();
        Object[] table = old != null && old[0] == dispatcher ? old.clone() : new Object[1 + 2 * SLOTS];
        table[0] = dispatcher;
        table[1 + 2 * slot] = optional;
        table[2 + 2 * slot] = value;
        holder.bons$setMowzieHandles(table);
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: mowziesmobs_capability_handles applies (Mowzie's Mobs' capability lookups reuse each entity's resolved {}){}",
                    values ? "value" : "handle", SHADOW ? " - shadow verification on" : "");
        }
    }
}
