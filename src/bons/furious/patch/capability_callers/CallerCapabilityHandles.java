package bons.furious.patch.capability_callers;

import bons.furious.mixin.capability_callers.CapabilityProviderCallerStateAccessor;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityDispatcher;
import net.minecraftforge.common.capabilities.CapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * The capability handle table of Bons and Furious switch forge_capability_handle_layer (since 1.0.46; Forge 47.4.16,
 * both sides), used by spawn_capability_handles, botania_capability_handles and clanginghowl_capability_handles. Ours
 * entirely; no code of any capability-owning mod is carried here. The same design as the private-token table of
 * cataclysm_capability_handles (PrivateCapabilityHandles, 1.0.36) with the 1.0.46 additions, in its own field so that
 * neither switch needs the other's mod.
 *
 * A mod registers the capabilities its per-tick helper asks for (register) and gets one slot per capability in a table
 * every LivingEntity carries (one volatile reference, CallerHandleTableHolder, null until first use). The table is an
 * Object[] replaced on every fill, never changed in place: [0] the entity's dispatcher at fill time, then per slot an
 * entry and a value.
 *
 * Entry kinds (each filled only for an entity whose class answers getCapability with Forge's own LivingEntity, Player
 * or AbstractHorse code, PLAIN: those answer only ITEM_HANDLER themselves and pass every other capability to
 * CapabilityProvider.getCapability, i.e. to the dispatcher; and only while the capabilities are valid):
 *  - a LazyOptional: the first present answer of entity.getCapability(cap). lookup hands it out again while (1) the
 *    capabilities are valid (an invalid provider answers empty without asking anyone, so the original runs), (2) the
 *    entity still has the same dispatcher object and (3) the kept LazyOptional is still present. Exact for a capability
 *    answered only by its owner's provider with the same final LazyOptional field on every ask, while earlier providers
 *    answer as a function of the Capability object (the premise of every capability cache, ModernFix's
 *    faster_capabilities included): the dispatcher returns the first present answer of its final caps array.
 *  - ABSENT: the dispatcher found no present answer (owners whose providers answer with a final LazyOptional field
 *    only). Under the same premise every provider answers with the same LazyOptional each time and none of those is
 *    present; a LazyOptional never becomes present again (Forge's isValid only goes from true to false), so the
 *    dispatcher answers LazyOptional.empty() for as long as the entity keeps that dispatcher, valid or not.
 *  - a value with its LazyOptional (keepFresh/keptFresh): a provider that builds a new LazyOptional per ask (Clanging
 *    Howl: LazyOptional.of(() -> this.instance)) cannot be answered with the kept optional (the original hands out a new
 *    one each time), so the value resolved from the answer is kept with the optional it came from and handed out while
 *    (1), (2) and that optional is still present (nobody else holds Clanging Howl's new optional; a provider before it
 *    answering with its own final optional is caught when that optional is invalidated). Exact while the provider's
 *    field is written only by its constructor (guard lines and a census for that switch).
 *
 * The shadow mode of each switch (its .shadow property) asks the entity again on every hand-out and counts answers that
 * are not the identical object (the asked answer is then returned).
 */
public final class CallerCapabilityHandles {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    private static final Object LOCK = new Object();
    private static volatile int registeredSlots;
    /** The layer's mixins are in the game (forge_capability_handle_layer on): otherwise every switch asks the entity. */
    static final boolean LAYER = CallerHandleTableHolder.class.isAssignableFrom(LivingEntity.class)
            && CapabilityProviderCallerStateAccessor.class.isAssignableFrom(CapabilityProvider.class);
    public static final Object ABSENT = new Object() {
        @Override
        public String toString() {
            return "bons_and_furious:absent";
        }
    };
    public static final Object MISS = new Object() {
        @Override
        public String toString() {
            return "bons_and_furious:miss";
        }
    };

    private CallerCapabilityHandles() {
    }

    /** One switch's capabilities, the slots they occupy and its shadow counters. */
    public static final class Owner {
        final String key;
        final Capability<?>[] caps;
        final int base;
        final boolean shadow;
        final AtomicLong shadowChecks, shadowMismatches;
        final boolean absent;
        volatile boolean announced;

        Owner(String key, Capability<?>[] caps, int base, boolean shadow, AtomicLong shadowChecks, AtomicLong shadowMismatches, boolean absent) {
            this.key = key;
            this.caps = caps;
            this.base = base;
            this.shadow = shadow;
            this.shadowChecks = shadowChecks;
            this.shadowMismatches = shadowMismatches;
            this.absent = absent;
        }

        int slot(Capability<?> cap) {
            Capability<?>[] c = this.caps;
            for (int i = 0; i < c.length; i++) if (c[i] == cap) return this.base + i;
            return -1;
        }
    }

    /** Registers a switch's capabilities; absent entries only when every provider of them answers with a final LazyOptional field. */
    public static Owner register(String key, boolean shadow, AtomicLong shadowChecks, AtomicLong shadowMismatches, boolean absent, Capability<?>... caps) {
        Capability<?>[] kept = Arrays.stream(caps).filter(c -> c != null).distinct().toArray(Capability<?>[]::new);
        if (!LAYER) LOGGER.info("Bons and Furious: {} stands down: forge_capability_handle_layer is off, so there is no table to keep answers in", key);
        synchronized (LOCK) {
            int base = registeredSlots;
            registeredSlots = base + kept.length;
            return new Owner(key, kept, base, shadow, shadowChecks, shadowMismatches, absent);
        }
    }

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

    /** entity.getCapability(cap), answered from the entity's kept LazyOptional or absent entry when that is the same answer. */
    @SuppressWarnings("unchecked")
    public static <T> LazyOptional<T> lookup(Entity entity, Capability<T> cap, Owner owner) {
        if (!LAYER || !(entity instanceof LivingEntity)) return entity.getCapability(cap);
        int slot = owner.slot(cap);
        if (slot < 0) return entity.getCapability(cap);
        CapabilityProviderCallerStateAccessor state = (CapabilityProviderCallerStateAccessor) entity;
        CallerHandleTableHolder holder = (CallerHandleTableHolder) entity;
        boolean valid = state.bons$callerCapsValid();
        CapabilityDispatcher dispatcher = state.bons$callerCapDispatcher();
        Object[] table = holder.bons$callerHandles();
        if (table != null && dispatcher != null && table[0] == dispatcher && 2 + 2 * slot < table.length) {
            Object kept = table[1 + 2 * slot];
            if (kept == ABSENT) {
                LazyOptional<T> empty = LazyOptional.empty();
                return owner.shadow ? shadow(entity, cap, owner, empty) : empty;
            }
            if (kept instanceof LazyOptional<?> optional && table[2 + 2 * slot] == null && valid && optional.isPresent())
                return owner.shadow ? shadow(entity, cap, owner, (LazyOptional<T>) optional) : (LazyOptional<T>) optional;
        }
        LazyOptional<T> answer = entity.getCapability(cap);
        if (valid && dispatcher != null && PLAIN.get(entity.getClass())) {
            if (answer.isPresent()) keep(holder, slot, dispatcher, answer, null, owner);
            else if (owner.absent && answer == LazyOptional.empty()) keep(holder, slot, dispatcher, ABSENT, null, owner);
        }
        return answer;
    }

    /**
     * For a provider that builds a new LazyOptional per ask: the kept value, or MISS (the original runs). Reads fields only.
     * The entry also holds the LazyOptional the value was resolved from, and the value is handed out only while that one is
     * still present: for Clanging Howl's own provider it is a new optional nobody else holds (always present), and if another
     * provider answered first with its own final optional, invalidating that optional sends the next ask to the original.
     */
    public static Object keptFresh(Entity entity, Capability<?> cap, Owner owner) {
        if (!LAYER || !(entity instanceof LivingEntity)) return MISS;
        int slot = owner.slot(cap);
        if (slot < 0) return MISS;
        Object[] table = ((CallerHandleTableHolder) entity).bons$callerHandles();
        if (table == null || 2 + 2 * slot >= table.length) return MISS;
        Object value = table[2 + 2 * slot];
        if (value == null || !(table[1 + 2 * slot] instanceof LazyOptional<?> optional)) return MISS;
        CapabilityProviderCallerStateAccessor state = (CapabilityProviderCallerStateAccessor) entity;
        CapabilityDispatcher dispatcher = state.bons$callerCapDispatcher();
        if (dispatcher == null || table[0] != dispatcher || !state.bons$callerCapsValid() || !optional.isPresent()) return MISS;
        return value;
    }

    /** At the original resolution of such a provider's answer: keeps the value (non-null) with the optional it came from. */
    public static void keepFresh(Entity entity, Capability<?> cap, Owner owner, LazyOptional<?> optional, Object value) {
        if (!LAYER || value == null || optional == null || !(entity instanceof LivingEntity)) return;
        int slot = owner.slot(cap);
        if (slot < 0) return;
        CapabilityProviderCallerStateAccessor state = (CapabilityProviderCallerStateAccessor) entity;
        CapabilityDispatcher dispatcher = state.bons$callerCapDispatcher();
        if (!state.bons$callerCapsValid() || dispatcher == null || !optional.isPresent() || !PLAIN.get(entity.getClass())) return;
        keep((CallerHandleTableHolder) entity, slot, dispatcher, optional, value, owner);
    }

    /** Shadow check of a kept value handed out by a switch's own mixin: the original's result wins. */
    public static Object shadowValue(Entity entity, Capability<?> cap, Owner owner, Object ours, Object asked) {
        owner.shadowChecks.incrementAndGet();
        if (asked != ours && owner.shadowMismatches.incrementAndGet() <= 20)
            LOGGER.warn("Bons and Furious: {} shadow mismatch (value) on {} for {}", owner.key, entity, cap == null ? null : cap.getName());
        return asked;
    }

    private static <T> LazyOptional<T> shadow(Entity entity, Capability<T> cap, Owner owner, LazyOptional<T> kept) {
        LazyOptional<T> asked = entity.getCapability(cap);
        owner.shadowChecks.incrementAndGet();
        if (asked != kept && owner.shadowMismatches.incrementAndGet() <= 20)
            LOGGER.warn("Bons and Furious: {} shadow mismatch on {} for {}", owner.key, entity, cap.getName());
        return asked;
    }

    private static void keep(CallerHandleTableHolder holder, int slot, CapabilityDispatcher dispatcher, Object entry, Object value, Owner owner) {
        Object[] old = holder.bons$callerHandles();
        int length = 1 + 2 * Math.max(registeredSlots, slot + 1);
        Object[] table = old != null && old[0] == dispatcher ? Arrays.copyOf(old, Math.max(old.length, length)) : new Object[length];
        table[0] = dispatcher;
        table[1 + 2 * slot] = entry;
        table[2 + 2 * slot] = value;
        holder.bons$setCallerHandles(table);
        if (!owner.announced) {
            owner.announced = true;
            LOGGER.info("Bons and Furious: {} applies (its capability lookups reuse each living entity's resolved {}){}",
                    owner.key, value != null ? "value" : "handle", owner.shadow ? " - shadow verification on" : "");
        }
    }
}
