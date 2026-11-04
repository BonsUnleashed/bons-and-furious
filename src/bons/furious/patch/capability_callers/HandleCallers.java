package bons.furious.patch.capability_callers;

import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;

/**
 * One handle-form caller switch of forge_capability_handle_layer (since 1.0.49): its runtime flag
 * (-Dbons_and_furious.<camelName>=false asks the entity), shadow mode (.shadow=true: the layer asks the entity again on every
 * hand-out and counts non-identical answers in shadowChecks / shadowMismatches) and its owner in the handle table. References
 * no mod class: each switch's own holder class (EeeabHandles, WitherHandles, CelestisynthHandles, L2HostilityHandles,
 * L2LibraryHandles) names its mod's capability constants, so a missing mod only affects its own switch.
 *
 * Why a kept handle is the same answer: CallerCapabilityHandles hands out the first present answer of the entity's
 * dispatcher again only while the entity's capabilities are valid, its dispatcher is the same object and the kept
 * LazyOptional is still present, for entity classes that do not override getCapability; an absent entry only while the
 * dispatcher is the same. Each switch's providers answer with one final (or constructor-written) LazyOptional field (guard
 * lines and censuses per switch).
 */
public final class HandleCallers {
    public volatile boolean enabled;
    public final boolean shadow;
    public final AtomicLong shadowChecks = new AtomicLong(), shadowMismatches = new AtomicLong();
    final String key;
    final boolean absent;
    volatile CallerCapabilityHandles.Owner owner;

    public HandleCallers(String key, String camel, boolean absent) {
        this.key = key;
        this.enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious." + camel, "true"));
        this.shadow = Boolean.getBoolean("bons_and_furious." + camel + ".shadow");
        this.absent = absent;
    }

    public HandleCallers register(Capability<?>... caps) {
        this.owner = CallerCapabilityHandles.register(key, shadow, shadowChecks, shadowMismatches, absent, caps);
        return this;
    }

    /** A further owner of this switch (capabilities registered on first use). */
    public CallerCapabilityHandles.Owner registerMore(Capability<?>... caps) {
        return CallerCapabilityHandles.register(key, shadow, shadowChecks, shadowMismatches, absent, caps);
    }

    /** entity.getCapability(cap) through the layer (the entity is asked when the switch is off). */
    public <T> LazyOptional<T> lookup(Entity entity, Capability<T> cap) {
        CallerCapabilityHandles.Owner o = this.owner;
        return enabled && o != null ? CallerCapabilityHandles.lookup(entity, cap, o) : entity.getCapability(cap);
    }

    /** The same with an explicit owner. */
    public <T> LazyOptional<T> lookup(Entity entity, Capability<T> cap, CallerCapabilityHandles.Owner o) {
        return enabled && o != null ? CallerCapabilityHandles.lookup(entity, cap, o) : entity.getCapability(cap);
    }
}
