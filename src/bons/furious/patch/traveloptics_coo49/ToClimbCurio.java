package bons.furious.patch.traveloptics_coo49;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.redspace.ironsspellbooks.registries.MobEffectRegistry;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.common.util.LazyOptional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch traveloptics_climb_curio_effect_first (T.O Magic 'n Extras 6.3.0, All Rights Reserved; both sides;
 * exact). Ours; no T.O code is carried. Idea: Collections Of Optimizations (traveloptics.leanClimbCurioScan), idea text only.
 *
 * What the mod does. ForgeServerEvents.applyClimbOnCurio runs on Forge's living tick for every living entity on both sides:
 * CuriosApi.getCuriosInventory(entity).map(inv -> !inv.findCurios(stack -> stack != null && stack.is(sigil)).isEmpty())
 * .orElse(false), and only when that is true it asks hasEffect(Spider Aspect) and, with both, lets the entity climb. The
 * findCurios walk visits every slot of every slot type of a wearer's inventory, builds a list of slot results with their
 * contexts, through two lambdas and a LazyOptional, every tick: for every player and every mob with curio slots.
 *
 * What the switch does (ToClimbCurioPrecheckMixin, on the getCuriosInventory call). The original answer is taken; when it
 * holds an inventory, the entity does NOT have Spider Aspect, and the shared Curios layer (curios_presence, reached by name:
 * CuriosPresence.quiet) says a Curios walk over that entity's inventory now would apply no pending slot update and only read
 * (Curios' own inventory and slot handler classes, no slot count change waiting), the empty optional stands in: the
 * handler's map gives "no sigil", the test is false and the handler returns, exactly as the original ends without Spider
 * Aspect. What no longer runs is the walk (reads), the filter (reads: an item identity test and the sigil's registry entry)
 * and its result objects. An entity with Spider Aspect, an inventory the layer cannot vouch for (a pending slot update,
 * another mod's inventory or slot handler, the layer off or absent), or no inventory at all (then the original is already
 * the empty answer): the original answer, and the handler runs as shipped. hasEffect is asked before the walk instead of
 * after it: a read of the entity's effect map.
 *
 * Runtime flag: -Dbons_and_furious.travelopticsClimbCurioEffectFirst=false.
 */
public final class ToClimbCurio {
    public static final String KEY = "traveloptics_climb_curio_effect_first";
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.travelopticsClimbCurioEffectFirst", "true"));
    private static final String PRESENCE = "bons.furious.patch.curios_presence.CuriosPresence";
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    private static final MethodHandle QUIET;
    private static volatile boolean announced;
    /** Statistics for probes (plain field): walks left out (entities with an inventory, without Spider Aspect, quiet). */
    public static long skipped;

    static {
        MethodHandle q = null;
        try {
            q = MethodHandles.lookup().findStatic(Class.forName(PRESENCE, false, ToClimbCurio.class.getClassLoader()), "quiet",
                    MethodType.methodType(boolean.class, LivingEntity.class));
        } catch (ReflectiveOperationException | LinkageError e) {
            LOGGER.warn("Bons and Furious: {} cannot reach {}.quiet ({}); the stock code runs", KEY, PRESENCE, e.toString());
        }
        QUIET = q;
    }

    private ToClimbCurio() {
    }

    private static boolean quiet(LivingEntity entity) {
        try {
            return QUIET != null && (boolean) QUIET.invokeExact(entity);
        } catch (Throwable t) {
            return false;
        }
    }

    /** CuriosApi.getCuriosInventory(entity) in applyClimbOnCurio, wrapped. */
    @SuppressWarnings({"rawtypes"})
    public static LazyOptional inventory(LivingEntity entity, Operation<LazyOptional> original) {
        LazyOptional inventory = original.call(entity);
        if (!enabled || inventory == null || !inventory.isPresent() || QUIET == null) return inventory;
        if (entity.m_21023_((MobEffect) MobEffectRegistry.SPIDER_ASPECT.get())) return inventory;
        if (!quiet(entity)) return inventory;
        skipped++;
        if (!announced) announce();
        return LazyOptional.empty();
    }

    private static void announce() {
        announced = true;
        LOGGER.info("Bons and Furious: {} applies (T.O's climbing sigil walks the curios only of entities with Spider Aspect)", KEY);
    }
}
