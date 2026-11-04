package bons.furious.patch.capability_callers;

import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;

/**
 * forge_capability_handle_layer's facade for caller switches in OTHER groups (since 1.0.49). Groups compile on their own,
 * so a caller reaches these static methods through MethodHandles looked up by
 * name; every signature uses JDK, Minecraft and Forge types only, and the owner token is an Object (a
 * CallerCapabilityHandles.Owner). Nothing here adds behaviour: each method is the CallerCapabilityHandles method of the
 * same name (see that class for the rules and why the answers are identical).
 */
public final class CapabilityHandleApi {
    private CapabilityHandleApi() {
    }

    /** Registers one switch's capabilities (call once, from the caller helper's static init); returns its owner token. */
    public static Object register(String key, boolean shadow, AtomicLong shadowChecks, AtomicLong shadowMismatches, boolean absent, Capability<?>[] caps) {
        return CallerCapabilityHandles.register(key, shadow, shadowChecks, shadowMismatches, absent, caps);
    }

    /** entity.getCapability(cap), from the entity's kept handle or absent entry when that is the same answer. */
    public static LazyOptional<?> lookup(Entity entity, Capability<?> cap, Object owner) {
        return CallerCapabilityHandles.lookup(entity, cap, (CallerCapabilityHandles.Owner) owner);
    }

    /** level.getCapability(cap), from the level's kept handle or absent entry when that is the same answer. */
    public static LazyOptional<?> lookupLevel(Level level, Capability<?> cap, Object owner) {
        return CallerCapabilityHandles.lookupLevel(level, cap, (CallerCapabilityHandles.Owner) owner);
    }

    /** Value form: the kept value for a provider that builds a new LazyOptional per ask, or miss(). */
    public static Object keptFresh(Entity entity, Capability<?> cap, Object owner) {
        return CallerCapabilityHandles.keptFresh(entity, cap, (CallerCapabilityHandles.Owner) owner);
    }

    /** Value form: keeps a present value with the optional it came from (call it at the original's orElse). */
    public static void keepFresh(Entity entity, Capability<?> cap, Object owner, LazyOptional<?> optional, Object value) {
        CallerCapabilityHandles.keepFresh(entity, cap, (CallerCapabilityHandles.Owner) owner, optional, value);
    }

    /** Shadow-mode compare of a kept value with the original's answer (returns the original's). */
    public static Object shadowValue(Entity entity, Capability<?> cap, Object owner, Object ours, Object asked) {
        return CallerCapabilityHandles.shadowValue(entity, cap, (CallerCapabilityHandles.Owner) owner, ours, asked);
    }

    /** The token keptFresh returns when the original must run. */
    public static Object miss() {
        return CallerCapabilityHandles.MISS;
    }

    /** True when the entity half of the layer is in the game. */
    public static boolean layer() {
        return CallerCapabilityHandles.LAYER;
    }

    /** True when the Level half of the layer is in the game. */
    public static boolean levelLayer() {
        return CallerCapabilityHandles.LEVEL_LAYER;
    }
}
