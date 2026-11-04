package bons.furious.patch.capability_callers;

import com.Polarice3.Goety.common.capabilities.lichdom.LichProvider;
import com.Polarice3.Goety.common.capabilities.misc.MiscProvider;
import com.Polarice3.Goety.common.capabilities.soulenergy.SEProvider;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch goety_capability_handles (Goety 2.5.60.0, MIT; both sides; since 1.0.49). Idea: Collections Of
 * Optimizations (goety.cacheCapabilityOptional, goety.skipCapabilityFallback, goety.fastEmptyAllyCheck), idea text only;
 * the design is the clanginghowl_capability_handles one on forge_capability_handle_layer. No Goety code here: this class only
 * names the three capability constants.
 *
 * Goety's helpers SEHelper.getCapability(Player) = getCapability(SEProvider.CAPABILITY).orElse(new SEImp()),
 * LichdomHelper.getCapability(Player) = ...orElse(new LichImp()) and MiscCapHelper.getCapability(LivingEntity) =
 * ...orElseGet(MiscImp::new) are called from 139 sites, several per entity per tick: each is a dispatcher walk over the
 * entity's providers, a new LazyOptional and supplier from Goety's provider (LazyOptional.of(() -> this.instance)) and, for
 * SE and Lich, an eagerly built fallback (SEImp alone is ~19 objects) thrown away whenever the capability is present. The
 * providers' instance fields are written only by their constructors (census of the 7 Goety-family jars, guard lines), so
 * the resolved value is kept with the optional it came from in the layer's table and handed out while the entity's
 * capabilities are valid, its dispatcher is the same object and that optional is still present (value form). The
 * fallbacks' constructors only set their own fields, so not building an unused one changes nothing; when the capability is
 * not present the original runs and builds it.
 *
 * SEHelper.isAlly(Player, LivingEntity) is getAllyEntities(p).contains(e) || getAllyEntityTypes(p).contains(e.getType()):
 * both helpers build a new list from the SE capability's ally sets (EntityFinder per UUID). With both sets empty both lists
 * are empty and the answer is false: answered at once (one capability read, the same value) instead.
 *
 * -Dbons_and_furious.goetyCapabilityHandles=false asks the entity every time; .shadow=true (verification runs only) runs
 * the original on every hand-out and on every isAlly fast answer and counts non-identical / unequal answers.
 */
public final class GoetyHandles {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.goetyCapabilityHandles", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.goetyCapabilityHandles.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    public static final Capability<?> SE = SEProvider.CAPABILITY, LICH = LichProvider.CAPABILITY, MISC = MiscProvider.CAPABILITY;
    static final CallerCapabilityHandles.Owner OWNER = CallerCapabilityHandles.register("goety_capability_handles", SHADOW, SHADOW_CHECKS,
            SHADOW_MISMATCHES, false, SE, LICH, MISC);
    public static final Object MISS = CallerCapabilityHandles.MISS;
    private static volatile boolean announcedAlly;

    private GoetyHandles() {
    }

    /** The kept value for one helper, or MISS (the original runs). */
    public static Object kept(Entity entity, Capability<?> cap) {
        if (!enabled) return MISS;
        return CallerCapabilityHandles.keptFresh(entity, cap, OWNER);
    }

    /** At a helper's orElse / orElseGet: keeps a present value with the optional it came from. */
    public static void keep(Entity entity, Capability<?> cap, LazyOptional<?> optional, Object value) {
        if (enabled) CallerCapabilityHandles.keepFresh(entity, cap, OWNER, optional, value);
    }

    public static Object shadow(Entity entity, Capability<?> cap, Object ours, Object asked) {
        return CallerCapabilityHandles.shadowValue(entity, cap, OWNER, ours, asked);
    }

    /** isAlly shadow compare (fast answer false vs the original's). */
    public static boolean shadowAlly(boolean asked) {
        SHADOW_CHECKS.incrementAndGet();
        if (asked && SHADOW_MISMATCHES.incrementAndGet() <= 20)
            LOGGER.warn("Bons and Furious: goety_capability_handles shadow mismatch: isAlly answered true with both ally sets empty");
        return asked;
    }

    public static void announceAlly() {
        if (!announcedAlly) {
            announcedAlly = true;
            LOGGER.info("Bons and Furious: goety_capability_handles: SEHelper.isAlly answers players without allies at once");
        }
    }
}
