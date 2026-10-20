package bons.furious.patch.capability_callers;

import com.mongoose.clanginghowl.common.capabilities.CHCapProvider;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.common.util.LazyOptional;

/**
 * Bons and Furious switch clanginghowl_capability_handles (Clanging Howl 1.0.4, both sides, since 1.0.46). No Clanging
 * Howl code here: this class only names CHCapProvider.CAPABILITY.
 *
 * Clanging Howl's CHCapHelper.getCapability(entity) is getCapability(CAPABILITY).orElse(new CHCapImp()), called from its
 * per-tick handlers for every living entity (0.52 % of the 1.0.44 entity-scene server thread): a dispatcher walk, a new
 * LazyOptional and supplier lambda from CHCapProvider (LazyOptional.of(() -> this.instance)) and a CHCapImp that is
 * thrown away whenever the capability is present. The provider is attached to every LivingEntity and its instance field
 * is written only by its constructor (guard lines + census), so the value it resolves is kept in the
 * forge_capability_handle_layer table (CallerCapabilityHandles.keepFresh, with the optional it came from) and
 * CHCapHelperValueMixin returns it while the entity's capabilities are valid, its dispatcher is the same object and that
 * optional is still present. CHCapImp's constructor only sets its own
 * fields, so not building the unused fallback changes nothing; when the capability is not present (an invalid provider
 * included) the original runs and builds it as before.
 *
 * -Dbons_and_furious.clanginghowlCapabilityHandles=false asks the dispatcher every time; .shadow=true (verification runs
 * only) runs the original on every hand-out and counts non-identical answers in SHADOW_CHECKS / SHADOW_MISMATCHES.
 */
public final class ClangingHowlHandles {
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.clanginghowlCapabilityHandles", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.clanginghowlCapabilityHandles.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    static final CallerCapabilityHandles.Owner OWNER = CallerCapabilityHandles.register("clanginghowl_capability_handles", SHADOW,
            SHADOW_CHECKS, SHADOW_MISMATCHES, false, CHCapProvider.CAPABILITY);

    private ClangingHowlHandles() {
    }

    /** The kept value for CHCapHelper.getCapability(entity), or CallerCapabilityHandles.MISS (the original runs). */
    public static Object kept(LivingEntity entity) {
        if (!enabled) return CallerCapabilityHandles.MISS;
        return CallerCapabilityHandles.keptFresh(entity, CHCapProvider.CAPABILITY, OWNER);
    }

    /** At the original orElse: the present value of the capability on this entity and the optional it came from. */
    public static void keep(LivingEntity entity, LazyOptional<?> optional, Object value) {
        if (enabled) CallerCapabilityHandles.keepFresh(entity, CHCapProvider.CAPABILITY, OWNER, optional, value);
    }

    public static Object shadow(LivingEntity entity, Object ours, Object asked) {
        return CallerCapabilityHandles.shadowValue(entity, CHCapProvider.CAPABILITY, OWNER, ours, asked);
    }
}
