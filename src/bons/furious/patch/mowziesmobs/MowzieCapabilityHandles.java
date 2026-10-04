package bons.furious.patch.mowziesmobs;

import bons.furious.mixin.mowziesmobs.CapabilityProviderStateAccessor;
import com.bobmowzie.mowziesmobs.server.capability.CapabilityHandler;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
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
 * CapabilityHandlerLookupMixin sends both asks here. For a LivingEntity and one of Mowzie's four capabilities
 * (FROZEN, LIVING, ABILITY, PLAYER) the first present answer is kept on the entity together with the dispatcher it came
 * from, and handed out again while (1) the entity's capabilities are valid (CapabilityProvider.valid: an invalidated
 * provider answers empty without asking anyone, so the original path runs), (2) the entity still has the same dispatcher
 * object and (3) the kept LazyOptional is still present (LazyOptional.isPresent: not invalidated). Any other entity,
 * capability or state runs the original ask.
 *
 * Why the answer is identical: Forge's CapabilityDispatcher.caps is a final array built once in the entity's
 * constructor and the dispatcher returns the first present answer in that order; each Mowzie provider answers its own
 * capability with this.instance.cast(), the same LazyOptional object every time (Capability.orEmpty), which Mowzie never
 * invalidates. So the dispatcher hands out the identical object on every ask as long as the providers before Mowzie's
 * answer a Mowzie capability the same way every time, the assumption every capability cache (ModernFix's
 * faster_capabilities included) rests on: providers answer as a function of the Capability object. Check (3) also
 * covers someone invalidating Mowzie's LazyOptional: the original walk then runs and finds the next provider, as before.
 *
 * -Dbons_and_furious.mowziesmobsCapabilityHandles.shadow=true (verification runs only) also asks the dispatcher whenever
 * a kept handle is handed out and counts any non-identical answer in SHADOW_CHECKS / SHADOW_MISMATCHES (the original
 * answer is then returned).
 */
public final class MowzieCapabilityHandles {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    /** Runtime switch (the config switch acts when classes are transformed). -Dbons_and_furious.mowziesmobsCapabilityHandles=false asks the dispatcher every time. */
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.mowziesmobsCapabilityHandles", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.mowziesmobsCapabilityHandles.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    private static volatile boolean announced;

    /** A kept answer: the dispatcher it came from and the LazyOptional it returned (immutable, so a racing reader sees it whole). */
    static final class Handle {
        final CapabilityDispatcher dispatcher;
        final LazyOptional<?> optional;

        Handle(CapabilityDispatcher dispatcher, LazyOptional<?> optional) {
            this.dispatcher = dispatcher;
            this.optional = optional;
        }
    }

    private MowzieCapabilityHandles() {
    }

    private static int slot(Capability<?> cap) {
        if (cap == CapabilityHandler.FROZEN_CAPABILITY) return 0;
        if (cap == CapabilityHandler.LIVING_CAPABILITY) return 1;
        if (cap == CapabilityHandler.ABILITY_CAPABILITY) return 2;
        if (cap == CapabilityHandler.PLAYER_CAPABILITY) return 3;
        return -1;
    }

    /** entity.getCapability(cap), answered from the entity's kept handle when that is the same answer. */
    @SuppressWarnings("unchecked")
    public static <T> LazyOptional<T> lookup(Entity entity, Capability<T> cap) {
        if (!enabled || !(entity instanceof LivingEntity)) return entity.getCapability(cap);
        int slot = slot(cap);
        if (slot < 0) return entity.getCapability(cap);
        CapabilityProviderStateAccessor state = (CapabilityProviderStateAccessor) entity;
        MowzieHandleHolder holder = (MowzieHandleHolder) entity;
        boolean valid = state.bons$mowzieCapsValid();
        CapabilityDispatcher dispatcher = state.bons$mowzieCapDispatcher();
        Handle[] handles = (Handle[]) holder.bons$mowzieHandles();
        if (valid && handles != null) {
            Handle h = handles[slot];
            if (h != null && h.dispatcher == dispatcher && h.optional.isPresent()) {
                if (!SHADOW) return (LazyOptional<T>) h.optional;
                LazyOptional<T> asked = entity.getCapability(cap);
                SHADOW_CHECKS.incrementAndGet();
                if (asked != h.optional && SHADOW_MISMATCHES.incrementAndGet() <= 20)
                    LOGGER.warn("Bons and Furious: mowziesmobs_capability_handles shadow mismatch on {} for {}", entity, cap.getName());
                return asked;
            }
        }
        LazyOptional<T> answer = entity.getCapability(cap);
        if (valid && dispatcher != null && answer.isPresent()) {
            if (handles == null) {
                handles = new Handle[4];
                holder.bons$setMowzieHandles(handles);
            }
            handles[slot] = new Handle(dispatcher, answer);
            if (!announced) {
                announced = true;
                LOGGER.info("Bons and Furious: mowziesmobs_capability_handles applies (Mowzie's Mobs' capability lookups reuse each entity's resolved handle){}",
                        SHADOW ? " - shadow verification on" : "");
            }
        }
        return answer;
    }
}
