package bons.furious.patch.cataclysm;

import com.github.L_Ender.cataclysm.init.ModCapabilities;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;

/**
 * Bons and Furious switch cataclysm_capability_handles (L_Ender's Cataclysm 3.16, both sides, since 1.0.36). No Cataclysm
 * code here (CC-BY-NC-ND-4.0): this class only names Cataclysm's five capability constants.
 *
 * Cataclysm's ModCapabilities.getCapability(entity, capability) asks the entity's capability dispatcher twice per call
 * (getCapability(cap).isPresent(), then getCapability(cap).orElseThrow(...)), and its ServerEventHandler.onLivingUpdateEvent
 * calls it three times per living entity per tick on both logical sides (hook, charge, render rush): six dispatcher
 * walks per entity per tick, measured at 0.29-0.74% of the integrated server thread with ModernFix's faster_capabilities
 * underneath. ModCapabilitiesLookupMixin sends both asks here; the five capabilities Cataclysm attaches to every
 * LivingEntity (HOOK, CHARGE, RENDER_RUSH, TENTACLE, PARRY: each answered only by Cataclysm's own provider, with a final
 * LazyOptional) are registered in the shared PrivateCapabilityHandles table, which hands out the identical
 * LazyOptional the walk returns (see there for the proof).
 *
 * Since 1.0.46 (value form): ModCapabilitiesValueMixin answers the whole getCapability from the kept value (Cataclysm's
 * own null and isAlive checks still run first) and keeps the value at the original orElseThrow; absent entries are on
 * (all five providers answer with a private final LazyOptional field, so PrivateCapabilityHandles' absent rule holds).
 *
 * -Dbons_and_furious.cataclysmCapabilityHandles=false asks the dispatcher every time;
 * -Dbons_and_furious.cataclysmCapabilityHandles.values=false keeps the 1.0.36 handle form only;
 * -Dbons_and_furious.cataclysmCapabilityHandles.shadow=true (verification runs only) asks it again on every hand-out and
 * counts non-identical answers in SHADOW_CHECKS / SHADOW_MISMATCHES (WARN for the first 20; the asked answer is returned).
 */
public final class CataclysmCapabilityHandles {
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.cataclysmCapabilityHandles", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.cataclysmCapabilityHandles.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    /** Hook, charge and render rush first: the three asked for every living entity every tick. */
    static final PrivateCapabilityHandles.Owner OWNER = PrivateCapabilityHandles.register("cataclysm_capability_handles", SHADOW,
            SHADOW_CHECKS, SHADOW_MISMATCHES, true, ModCapabilities.HOOK_CAPABILITY, ModCapabilities.CHARGE_CAPABILITY,
            ModCapabilities.RENDER_RUSH_CAPABILITY, ModCapabilities.TENTACLE_CAPABILITY, ModCapabilities.PARRY_CAPABILITY);

    static {
        OWNER.values = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.cataclysmCapabilityHandles.values", "true"));
    }

    private CataclysmCapabilityHandles() {
    }

    /** Since 1.0.46: the value form on or off at run time (false = the 1.0.36 handle form only). */
    public static void setValues(boolean on) {
        OWNER.values = on;
    }

    /** The redirect target of both entity.getCapability(capability) calls in ModCapabilities.getCapability. */
    public static <T> LazyOptional<T> lookup(Entity entity, Capability<T> capability) {
        if (!enabled) return entity.getCapability(capability);
        return PrivateCapabilityHandles.lookup(entity, capability, OWNER);
    }

    /** For ModCapabilitiesValueMixin: the kept value, PrivateCapabilityHandles.ABSENT, or PrivateCapabilityHandles.MISS. */
    public static Object kept(Entity entity, Capability<?> capability) {
        if (!enabled) return PrivateCapabilityHandles.MISS;
        return PrivateCapabilityHandles.kept(entity, capability, OWNER);
    }

    /** At the original orElseThrow in ModCapabilities.getCapability. */
    public static void keepValue(Entity entity, Capability<?> capability, LazyOptional<?> optional, Object value) {
        if (enabled) PrivateCapabilityHandles.keepValue(entity, capability, OWNER, optional, value);
    }

    public static Object shadowValue(Entity entity, Capability<?> capability, Object ours, Object asked) {
        return PrivateCapabilityHandles.shadowValue(entity, capability, OWNER, ours, asked);
    }
}
