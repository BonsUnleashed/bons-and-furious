package bons.furious.patch.torchmaster_coo49;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import net.xalcon.torchmaster.common.ModCaps;
import net.xalcon.torchmaster.common.logic.entityblocking.ITEBLightRegistry;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious 1.0.49 switch torchmaster_level_registry_handle (Torchmaster 20.1.9, server; kind change, off by default:
 * every answer is Torchmaster's, but the speed measured 0.91-1.17x of the original per spawn check depending on the JIT run
 * and the lights in the registry, so it stays opt-in; helper of TorchmasterRegistryHandleMixin). No Torchmaster code is
 * carried.
 *
 * What Torchmaster does. EntityBlockingEventHandler.shouldBlockEntity(entity, pos) runs for every mob about to be placed
 * (MobSpawnEvent.FinalizeSpawn: natural spawns, spawners, structures; also Torchmaster's MCA Reborn hook):
 * entity.getCommandSenderWorld().getCapability(TEB_REGISTRY) walks every capability provider any mod attached to the level,
 * then .map(reg -> reg.shouldBlockEntity(entity, pos)).orElse(false) builds a capturing lambda, an Optional and boxes the
 * answer.
 *
 * What the switch does. The capability is asked through forge_capability_handle_layer's Level table
 * (CapabilityHandleApi.lookupLevel, reached by name: the identical LazyOptional while the level's capabilities are valid,
 * its dispatcher is the same object and the kept optional is still present; otherwise the level is asked), and the answer
 * is isPresent() ? registry.shouldBlockEntity(entity, pos) : false, which is exactly what map(f).orElse(false) answers
 * (Forge's LazyOptional.map is isPresent() ? Optional.of(f(value)) : Optional.empty(), and f's Boolean is never null).
 * Torchmaster's provider answers TEB_REGISTRY with one LazyOptional field written only by its constructor (census on the
 * bytecode), so the kept handle is the one the dispatcher answers. Without the layer (switch off, an older build) the level
 * is asked every time and only the lambda / Optional / boxing are left out. Runtime flag
 * bons_and_furious.torchmasterLevelRegistryHandle; shadow mode bons_and_furious.torchmasterLevelRegistryHandle.shadow (the
 * layer asks the level again on every hand-out and counts handles that are not the identical object: SHADOW_CHECKS /
 * SHADOW_MISMATCHES, WARN for the first 20).
 */
public final class TorchmasterRegistryHandle {
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.torchmasterLevelRegistryHandle", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.torchmasterLevelRegistryHandle.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    private static final String API = "bons.furious.patch.capability_callers.CapabilityHandleApi";
    private static final MethodHandle REGISTER, LOOKUP_LEVEL;
    private static volatile boolean announced;

    static {
        MethodHandle r = null, l = null;
        try {
            Class<?> c = Class.forName(API, false, TorchmasterRegistryHandle.class.getClassLoader());
            MethodHandles.Lookup lk = MethodHandles.lookup();
            r = lk.findStatic(c, "register", MethodType.methodType(Object.class, String.class, boolean.class, AtomicLong.class, AtomicLong.class, boolean.class, Capability[].class));
            l = lk.findStatic(c, "lookupLevel", MethodType.methodType(LazyOptional.class, Level.class, Capability.class, Object.class));
        } catch (Throwable t) {
            r = null;
            l = null;
        }
        REGISTER = r;
        LOOKUP_LEVEL = l;
    }

    private TorchmasterRegistryHandle() {
    }

    /** The switch's owner token in the layer's table (registered on first use; null: ask the level every time). */
    private static final class Owner {
        static final Capability<?> CAP = ModCaps.TEB_REGISTRY;
        static final Object TOKEN = register();

        private static Object register() {
            Object token = null;
            if (REGISTER != null && CAP != null) {
                try {
                    token = (Object) REGISTER.invokeExact("torchmaster_level_registry_handle", SHADOW, SHADOW_CHECKS, SHADOW_MISMATCHES, false, new Capability<?>[]{CAP});
                } catch (Throwable t) {
                    token = null;
                }
            }
            if (token == null) {
                LOGGER.info("Bons and Furious: torchmaster_level_registry_handle applies without forge_capability_handle_layer's Level table (the level is asked each time; only the lambda, Optional and boxing are left out)");
                announced = true;
            }
            return token;
        }
    }

    /** For the harness's timing arms only (always true in the game): false asks the level directly, as without the layer. */
    public static volatile boolean layerLookups = true;

    /** level.getCapability(cap): the layer's kept handle when it is the same answer, else the level's own. */
    static LazyOptional<?> lookup(Level level, Capability<?> cap) {
        Object owner = Owner.TOKEN;
        if (owner != null && cap == Owner.CAP && layerLookups) {
            try {
                return (LazyOptional<?>) LOOKUP_LEVEL.invokeExact(level, cap, owner);
            } catch (Throwable t) {
                if (t instanceof RuntimeException re) throw re;
                if (t instanceof Error e) throw e;
                throw new IllegalStateException(t);
            }
        }
        return level.getCapability(cap);
    }

    /**
     * EntityBlockingEventHandler.shouldBlockEntity's answer, with the same capability ask and the same registry call. Forge
     * 47.4.28's map(f) is isPresent() ? Optional.of(f(getValueUnsafe())) : Optional.empty(); getValueUnsafe() throws this
     * IllegalStateException when the value resolves to null (a supplier that answers null: Forge logs it and keeps null), and
     * orElse(null) resolves exactly as getValueUnsafe() does, so the same exception is thrown in that case.
     */
    public static boolean shouldBlock(Entity entity, BlockPos pos) {
        Level world = entity.m_20193_();
        LazyOptional<?> optional = lookup(world, ModCaps.TEB_REGISTRY);
        if (!optional.isPresent()) return false;
        Object registry = optional.orElse(null);
        if (registry == null) throw new IllegalStateException("LazyOptional is empty or otherwise returned null from getValue() unexpectedly");
        return ((ITEBLightRegistry) registry).shouldBlockEntity(entity, pos);
    }
}
