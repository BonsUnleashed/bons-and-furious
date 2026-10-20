package bons.furious.patch.capability_callers;

import com.ninni.spawn.server.gui.fly.FlyCapabilityProvider;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;

/**
 * Bons and Furious switch spawn_capability_handles (Spawn 4.0.7, both sides, since 1.0.46). No Spawn code here: this
 * class only names Spawn's FLY_CAP constant.
 *
 * Spawn's ForgeEvents.livingTick asks every living entity for FLY_CAP every tick (getCapability(FLY_CAP).ifPresent(...):
 * one dispatcher walk per living entity per tick, 0.62 % of the 1.0.44 entity-scene server thread). FlyCapabilityProvider
 * (attached to every Mob and Player) answers FLY_CAP with its private final LazyOptional field, so the answer is kept in
 * the forge_capability_handle_layer table (CallerCapabilityHandles: the LazyOptional entry, or absent for living entities
 * that are neither Mob nor Player) and handed out again while it is the same answer.
 *
 * -Dbons_and_furious.spawnCapabilityHandles=false asks the dispatcher every time; .shadow=true (verification runs only)
 * asks it again on every hand-out and counts non-identical answers in SHADOW_CHECKS / SHADOW_MISMATCHES.
 */
public final class SpawnFlyHandles {
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.spawnCapabilityHandles", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.spawnCapabilityHandles.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    static final CallerCapabilityHandles.Owner OWNER = CallerCapabilityHandles.register("spawn_capability_handles", SHADOW,
            SHADOW_CHECKS, SHADOW_MISMATCHES, true, FlyCapabilityProvider.FLY_CAP);

    private SpawnFlyHandles() {
    }

    /** The redirect target of livingTick's entity.getCapability(FLY_CAP). */
    public static <T> LazyOptional<T> lookup(LivingEntity entity, Capability<T> capability) {
        if (!enabled) return entity.getCapability(capability);
        return CallerCapabilityHandles.lookup(entity, capability, OWNER);
    }
}
