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
 * -Dbons_and_furious.cataclysmCapabilityHandles=false asks the dispatcher every time;
 * -Dbons_and_furious.cataclysmCapabilityHandles.shadow=true (verification runs only) asks it again on every hand-out and
 * counts non-identical answers in SHADOW_CHECKS / SHADOW_MISMATCHES (WARN for the first 20; the asked answer is returned).
 */
public final class CataclysmCapabilityHandles {
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.cataclysmCapabilityHandles", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.cataclysmCapabilityHandles.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    /** Hook, charge and render rush first: the three asked for every living entity every tick. */
    static final PrivateCapabilityHandles.Owner OWNER = PrivateCapabilityHandles.register("cataclysm_capability_handles", SHADOW,
            SHADOW_CHECKS, SHADOW_MISMATCHES, ModCapabilities.HOOK_CAPABILITY, ModCapabilities.CHARGE_CAPABILITY,
            ModCapabilities.RENDER_RUSH_CAPABILITY, ModCapabilities.TENTACLE_CAPABILITY, ModCapabilities.PARRY_CAPABILITY);

    private CataclysmCapabilityHandles() {
    }

    /** The redirect target of both entity.getCapability(capability) calls in ModCapabilities.getCapability. */
    public static <T> LazyOptional<T> lookup(Entity entity, Capability<T> capability) {
        if (!enabled) return entity.getCapability(capability);
        return PrivateCapabilityHandles.lookup(entity, capability, OWNER);
    }
}
