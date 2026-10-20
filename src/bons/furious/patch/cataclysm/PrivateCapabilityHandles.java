package bons.furious.patch.cataclysm;

import bons.furious.mixin.cataclysm.CapabilityProviderTokenStateAccessor;
import java.util.Arrays;
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
 * The shared "mod-private capability token" table behind Bons and Furious switch cataclysm_capability_handles (since
 * 1.0.36; Forge 47.4.16, both sides). Ours entirely; no code of any capability-owning mod is carried here.
 *
 * Generalises the design shipped in 1.0.28 as mowziesmobs_capability_handles (MowzieCapabilityHandles): a mod registers
 * its private capabilities once (register) and gets one slot per capability in a single table that every LivingEntity
 * carries (one reference field, HandleTableHolder, null until a registered capability is first resolved on that entity).
 * A later mod costs a register call and a two-line redirect, not another field on every entity.
 *
 * A slot keeps the first present answer of entity.getCapability(cap) together with the dispatcher it came from. lookup
 * hands that answer out again only while (1) the entity's capabilities are valid (CapabilityProvider.valid; an invalid
 * provider answers empty without asking anyone, so the original path runs), (2) the entity still has the same dispatcher
 * object, and (3) the kept LazyOptional is still present (not invalidated). Any other state, entity or capability asks
 * the entity exactly as before. A slot is only ever filled for an entity whose class resolves getCapability to Forge's
 * own LivingEntity, Player or AbstractHorse declarations (checked once per class, PLAIN below): those answer only
 * ForgeCapabilities.ITEM_HANDLER themselves and pass every other capability to CapabilityProvider.getCapability, i.e. to
 * the dispatcher. Mod entities that declare their own getCapability (Ice and Fire dragons, the Robit, Guard Villagers'
 * guards, Netherite Ministrosity, ... 18 classes in this pack) always run the original lookup.
 *
 * Why the kept answer is the identical object: Forge's CapabilityDispatcher.caps is a final array built once by
 * gatherCapabilities and the dispatcher (ModernFix's generated one included) returns the first present answer in that
 * order. A registered private capability is answered only by its owner's provider, with Capability.orEmpty(cap,
 * this.instance.cast()): the same final LazyOptional object on every ask. Earlier providers answer a capability as a
 * function of the Capability object, the assumption every capability cache (ModernFix's faster_capabilities included)
 * rests on; check (3) covers anyone invalidating the kept LazyOptional (the walk then runs and finds the next provider,
 * as before). The shadow mode of each owner (its switch's .shadow property) asks the dispatcher again on every hand-out
 * and counts any answer that is not the identical object (the asked answer is then returned).
 *
 * Since 1.0.46 (value form; an owner's values flag turns it off at run time):
 *  - a slot also keeps the value the owner's orElseThrow returned for the kept LazyOptional (keepValue, called at that
 *    original call), and kept() hands it out for the owner's whole getCapability helper while (1)-(3) hold. A
 *    LazyOptional resolves its value once and keeps it (Forge's LazyOptional.getValue), and isValid only ever goes from
 *    true to false, so while the kept LazyOptional is present its orElseThrow returns the kept value;
 *  - an "absent" slot: a dispatcher ask that found no present answer while the capabilities were valid (PLAIN classes
 *    only, and only for owners whose providers answer with a final LazyOptional field). Under the same premise every
 *    provider of that dispatcher answers the capability with the same LazyOptional on every ask and none of those is
 *    present; a LazyOptional never becomes present again, so the dispatcher answers LazyOptional.empty() for as long as
 *    the entity keeps it (and an invalid provider answers empty too).
 * The table is one Object[] per entity: [0] the dispatcher, then per slot the LazyOptional (or ABSENT) and its value (or
 * null). It is replaced on every fill, never changed in place, and published through a volatile field, so a reader on
 * another thread sees whole entries.
 */
public final class PrivateCapabilityHandles {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    private static final Object LOCK = new Object();
    private static volatile int registeredSlots;
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

    private PrivateCapabilityHandles() {
    }

    /** One mod's private capabilities, the slots they occupy and the switch that uses them. */
    public static final class Owner {
        final String key;
        final Capability<?>[] caps;
        final int base;
        final boolean shadow;
        final AtomicLong shadowChecks, shadowMismatches;
        /** Absent entries allowed: every provider of these capabilities answers with a final LazyOptional field. */
        final boolean absent;
        /** Since 1.0.46: the value form and absent entries for this owner (false = the 1.0.36 handle form only). */
        public volatile boolean values = true;
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

        /** The table slot of a registered capability (identity), or -1. */
        int slot(Capability<?> cap) {
            Capability<?>[] c = this.caps;
            for (int i = 0; i < c.length; i++) if (c[i] == cap) return this.base + i;
            return -1;
        }
    }

    /**
     * Registers a mod's private capabilities (each must be answered only by that mod's own providers) for the switch
     * {@code key}. Null capabilities are kept out of the table (they can never match a lookup).
     */
    public static Owner register(String key, boolean shadow, AtomicLong shadowChecks, AtomicLong shadowMismatches, Capability<?>... caps) {
        return register(key, shadow, shadowChecks, shadowMismatches, false, caps);
    }

    /** As register, with absent entries allowed when {@code absent} (see the class comment for when that is exact). */
    public static Owner register(String key, boolean shadow, AtomicLong shadowChecks, AtomicLong shadowMismatches, boolean absent, Capability<?>... caps) {
        Capability<?>[] kept = Arrays.stream(caps).filter(c -> c != null).distinct().toArray(Capability<?>[]::new);
        synchronized (LOCK) {
            int base = registeredSlots;
            registeredSlots = base + kept.length;
            return new Owner(key, kept, base, shadow, shadowChecks, shadowMismatches, absent);
        }
    }

    /**
     * True when getCapability of every class between the entity's class and LivingEntity is Forge's own (LivingEntity,
     * Player, AbstractHorse: ITEM_HANDLER only, then the dispatcher). A class that declares either getCapability method
     * itself (or whose methods cannot be listed) is not plain: its entities always run the original lookup.
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

    /** entity.getCapability(cap), answered from the entity's kept slot when that is the same answer. */
    @SuppressWarnings("unchecked")
    public static <T> LazyOptional<T> lookup(Entity entity, Capability<T> cap, Owner owner) {
        if (!(entity instanceof LivingEntity)) return entity.getCapability(cap);
        int slot = owner.slot(cap);
        if (slot < 0) return entity.getCapability(cap);
        CapabilityProviderTokenStateAccessor state = (CapabilityProviderTokenStateAccessor) entity;
        HandleTableHolder holder = (HandleTableHolder) entity;
        boolean valid = state.bons$tokenCapsValid();
        CapabilityDispatcher dispatcher = state.bons$tokenCapDispatcher();
        Object[] table = holder.bons$tokenHandles();
        if (table != null && dispatcher != null && table[0] == dispatcher && 2 + 2 * slot < table.length) {
            Object kept = table[1 + 2 * slot];
            if (kept == ABSENT) {
                if (owner.values) {
                    LazyOptional<T> empty = LazyOptional.empty();
                    return owner.shadow ? shadow(entity, cap, owner, empty) : empty;
                }
            } else if (kept != null && valid && ((LazyOptional<?>) kept).isPresent()) {
                return owner.shadow ? shadow(entity, cap, owner, (LazyOptional<T>) kept) : (LazyOptional<T>) kept;
            }
        }
        LazyOptional<T> answer = entity.getCapability(cap);
        if (valid && dispatcher != null && PLAIN.get(entity.getClass())) {
            if (answer.isPresent()) keep(holder, slot, dispatcher, answer, owner);
            else if (owner.absent && owner.values && answer == LazyOptional.empty()) keep(holder, slot, dispatcher, ABSENT, owner);
        }
        return answer;
    }

    /**
     * For an owner's value mixin: the value its getCapability helper would return from its dispatcher asks, ABSENT when
     * those find nothing, MISS when nothing is kept (the original runs). Reads fields only; calls no entity method.
     */
    public static Object kept(Entity entity, Capability<?> cap, Owner owner) {
        if (!owner.values || !(entity instanceof LivingEntity)) return MISS;
        int slot = owner.slot(cap);
        if (slot < 0) return MISS;
        Object[] table = ((HandleTableHolder) entity).bons$tokenHandles();
        if (table == null || 2 + 2 * slot >= table.length) return MISS;
        CapabilityProviderTokenStateAccessor state = (CapabilityProviderTokenStateAccessor) entity;
        CapabilityDispatcher dispatcher = state.bons$tokenCapDispatcher();
        if (dispatcher == null || table[0] != dispatcher) return MISS;
        Object kept = table[1 + 2 * slot];
        if (kept == ABSENT) return ABSENT;
        Object value = table[2 + 2 * slot];
        if (value == null || !state.bons$tokenCapsValid() || !((LazyOptional<?>) kept).isPresent()) return MISS;
        return value;
    }

    /** At the owner's original orElseThrow: keeps its value next to the kept LazyOptional it was asked of. */
    public static void keepValue(Entity entity, Capability<?> cap, Owner owner, LazyOptional<?> optional, Object value) {
        if (!owner.values || value == null || !(entity instanceof LivingEntity)) return;
        int slot = owner.slot(cap);
        if (slot < 0) return;
        HandleTableHolder holder = (HandleTableHolder) entity;
        Object[] table = holder.bons$tokenHandles();
        if (table == null || 2 + 2 * slot >= table.length || table[1 + 2 * slot] != optional || table[2 + 2 * slot] == value) return;
        if (table[0] != ((CapabilityProviderTokenStateAccessor) entity).bons$tokenCapDispatcher()) return;
        Object[] next = table.clone();
        next[2 + 2 * slot] = value;
        holder.bons$setTokenHandles(next);
    }

    /** Shadow check of a value or absent answer handed out by an owner's value mixin: the original's result wins. */
    public static Object shadowValue(Entity entity, Capability<?> cap, Owner owner, Object ours, Object asked) {
        owner.shadowChecks.incrementAndGet();
        if (asked != ours && owner.shadowMismatches.incrementAndGet() <= 20)
            LOGGER.warn("Bons and Furious: {} shadow mismatch (value) on {} for {}", owner.key, entity, cap.getName());
        return asked;
    }

    private static <T> LazyOptional<T> shadow(Entity entity, Capability<T> cap, Owner owner, LazyOptional<T> kept) {
        LazyOptional<T> asked = entity.getCapability(cap);
        owner.shadowChecks.incrementAndGet();
        if (asked != kept && owner.shadowMismatches.incrementAndGet() <= 20)
            LOGGER.warn("Bons and Furious: {} shadow mismatch on {} for {}", owner.key, entity, cap.getName());
        return asked;
    }

    private static void keep(HandleTableHolder holder, int slot, CapabilityDispatcher dispatcher, Object optional, Owner owner) {
        Object[] old = holder.bons$tokenHandles();
        int length = 1 + 2 * Math.max(registeredSlots, slot + 1);
        Object[] table;
        if (old != null && old[0] == dispatcher) table = Arrays.copyOf(old, Math.max(old.length, length));
        else table = new Object[length];
        table[0] = dispatcher;
        table[1 + 2 * slot] = optional;
        table[2 + 2 * slot] = null;
        holder.bons$setTokenHandles(table);
        if (!owner.announced) {
            owner.announced = true;
            LOGGER.info("Bons and Furious: {} applies (its capability lookups reuse each living entity's resolved {}){}",
                    owner.key, owner.values ? "value" : "handle", owner.shadow ? " - shadow verification on" : "");
        }
    }
}
