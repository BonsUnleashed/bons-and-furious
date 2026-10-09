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
 */
public final class PrivateCapabilityHandles {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    private static final Object LOCK = new Object();
    private static volatile int registeredSlots;

    private PrivateCapabilityHandles() {
    }

    /** One mod's private capabilities, the slots they occupy and the switch that uses them. */
    public static final class Owner {
        final String key;
        final Capability<?>[] caps;
        final int base;
        final boolean shadow;
        final AtomicLong shadowChecks, shadowMismatches;
        volatile boolean announced;

        Owner(String key, Capability<?>[] caps, int base, boolean shadow, AtomicLong shadowChecks, AtomicLong shadowMismatches) {
            this.key = key;
            this.caps = caps;
            this.base = base;
            this.shadow = shadow;
            this.shadowChecks = shadowChecks;
            this.shadowMismatches = shadowMismatches;
        }

        /** The table slot of a registered capability (identity), or -1. */
        int slot(Capability<?> cap) {
            Capability<?>[] c = this.caps;
            for (int i = 0; i < c.length; i++) if (c[i] == cap) return this.base + i;
            return -1;
        }
    }

    /** A kept answer: the dispatcher it came from and the LazyOptional it returned (immutable, so a racing reader sees it whole). */
    static final class Handle {
        final CapabilityDispatcher dispatcher;
        final LazyOptional<?> optional;

        Handle(CapabilityDispatcher dispatcher, LazyOptional<?> optional) {
            this.dispatcher = dispatcher;
            this.optional = optional;
        }
    }

    /**
     * Registers a mod's private capabilities (each must be answered only by that mod's own providers) for the switch
     * {@code key}. Null capabilities are kept out of the table (they can never match a lookup).
     */
    public static Owner register(String key, boolean shadow, AtomicLong shadowChecks, AtomicLong shadowMismatches, Capability<?>... caps) {
        Capability<?>[] kept = Arrays.stream(caps).filter(c -> c != null).distinct().toArray(Capability<?>[]::new);
        synchronized (LOCK) {
            int base = registeredSlots;
            registeredSlots = base + kept.length;
            return new Owner(key, kept, base, shadow, shadowChecks, shadowMismatches);
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

    /** entity.getCapability(cap), answered from the entity's kept handle when that is the same answer. */
    @SuppressWarnings("unchecked")
    public static <T> LazyOptional<T> lookup(Entity entity, Capability<T> cap, Owner owner) {
        if (!(entity instanceof LivingEntity)) return entity.getCapability(cap);
        int slot = owner.slot(cap);
        if (slot < 0) return entity.getCapability(cap);
        CapabilityProviderTokenStateAccessor state = (CapabilityProviderTokenStateAccessor) entity;
        HandleTableHolder holder = (HandleTableHolder) entity;
        boolean valid = state.bons$tokenCapsValid();
        CapabilityDispatcher dispatcher = state.bons$tokenCapDispatcher();
        Object[] handles = holder.bons$tokenHandles();
        if (valid && handles != null && slot < handles.length) {
            Handle h = (Handle) handles[slot];
            if (h != null && h.dispatcher == dispatcher && h.optional.isPresent()) {
                if (!owner.shadow) return (LazyOptional<T>) h.optional;
                LazyOptional<T> asked = entity.getCapability(cap);
                owner.shadowChecks.incrementAndGet();
                if (asked != h.optional && owner.shadowMismatches.incrementAndGet() <= 20)
                    LOGGER.warn("Bons and Furious: {} shadow mismatch on {} for {}", owner.key, entity, cap.getName());
                return asked;
            }
        }
        LazyOptional<T> answer = entity.getCapability(cap);
        if (valid && dispatcher != null && answer.isPresent() && PLAIN.get(entity.getClass())) {
            int size = registeredSlots;
            if (handles == null || handles.length <= slot) {
                handles = handles == null ? new Object[Math.max(size, slot + 1)] : Arrays.copyOf(handles, Math.max(size, slot + 1));
                holder.bons$setTokenHandles(handles);
            }
            handles[slot] = new Handle(dispatcher, answer);
            if (!owner.announced) {
                owner.announced = true;
                LOGGER.info("Bons and Furious: {} applies (its capability lookups reuse each living entity's resolved handle){}",
                        owner.key, owner.shadow ? " - shadow verification on" : "");
            }
        }
        return answer;
    }
}
