package bons.furious.patch.mob_sunburn;

import bons.furious.guard.Declared;
import java.lang.invoke.MethodType;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.world.entity.Entity;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch vanilla_sun_burn_deferred_wet_check (Minecraft 1.20.1 on Forge 47.4.16; both sides, acts on the
 * server: the method returns false at once on a client level). No Minecraft code here. Idea: Gale/Leaf "Optimize sun burn
 * tick" (idea text only).
 *
 * Mob.isSunBurnTick (m_21527_; Zombie, AbstractSkeleton, Phantom and modded undead call it every tick in daylight) asks
 * isInWaterRainOrBubble() (m_20071_) before its light test and its random roll, although the answer only matters when both
 * pass (the roll succeeds with probability (light - 0.4) * 2 / 30, at most 4%). That question is isInWater (a field; Alex's
 * Caves adds a Bubbled-effect check), isInRain (two Level.isRainingAt calls: Slice & Dice's HEAD hook reads 3+ blocks above
 * each position even on sunny days; Artifacts' umbrella check) and isInBubbleColumn (Valkyrien Skies' wrapper and a block
 * read at the feet). The switch answers "false" in its place (MobSunBurnOrderMixin, first redirect) and asks the real
 * question just before canSeeSky (second redirect), i.e. only when the light test and the roll have passed and the powder
 * snow flags are clear: the same three conditions are combined in the same "and", so the result is the same; the random
 * roll is made exactly when vanilla makes it (it depends only on the light value).
 *
 * The question is moved only for classes whose isInWater / isInWaterRainOrBubble are Entity's own (so it has no side
 * effects: field reads, effect-map and item reads, block and heightmap reads in the mob's own column) and that declare
 * none of Entity's water-update methods below Entity, so the mob's tick reads its own chunk column (Entity.baseTick's
 * fluid update; Slice & Dice's baseTick hook as well in this pack) before isSunBurnTick in the same tick: the chunk's
 * UNKNOWN load ticket ends the tick the same with or without the skipped reads (the argument of spawn_flyless_particle_check).
 * Every other class keeps vanilla's order. Assumption (as there): a Mob class's tick reaches Entity.baseTick.
 *
 * -Dbons_and_furious.sunBurnDeferredWetCheck=false keeps vanilla's order. -Dbons_and_furious.sunBurnDeferredWetCheck.shadow=true
 * (verification runs) also asks the question at vanilla's point and compares it with the deferred answer whenever the
 * deferred one is asked (SHADOW_CHECKS / SHADOW_MISMATCHES, first 20 logged).
 */
public final class SunBurnOrder {
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.sunBurnDeferredWetCheck", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.sunBurnDeferredWetCheck.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    private static volatile boolean announced;
    /**
     * Entity's methods a mob class must still inherit from Entity for the question to move, each as {name, descriptor}: the wet
     * question itself (isInWaterRainOrBubble, isInWater) and Entity's water update (the two updateInWaterState... methods,
     * updateFluidHeightAndDoFluidPushing(TagKey, double) and Forge's two overloads of that name).
     */
    private static final Object[][] ENTITY_METHODS = {
            {"m_20071_", MethodType.methodType(boolean.class)},                                                  // isInWaterRainOrBubble
            {"m_20069_", MethodType.methodType(boolean.class)},                                                  // isInWater
            {"m_20073_", MethodType.methodType(boolean.class)},                                                  // updateInWaterStateAndDoFluidPushing
            {"m_20074_", MethodType.methodType(void.class)},                                                     // updateInWaterStateAndDoWaterCurrentPushing
            {"m_204031_", MethodType.methodType(boolean.class, net.minecraft.tags.TagKey.class, double.class)},  // updateFluidHeightAndDoFluidPushing
            {"updateFluidHeightAndDoFluidPushing", MethodType.methodType(void.class)},                           // Forge's
            {"updateFluidHeightAndDoFluidPushing", MethodType.methodType(void.class, java.util.function.Predicate.class)},
    };

    private SunBurnOrder() {
    }

    /**
     * Per class: true when the wet question may be moved (see the class comment). Each method is asked on its own through the JVM's
     * resolution (bons.furious.guard.Declared), not Class.getMethod / getDeclaredMethods: those resolve the types of every method
     * they walk, and on a dedicated server they threw for a mob class with a method naming a client-only class, after Forge's red
     * "invalid dist" ERROR line when that class is marked client-only; the class then kept vanilla's order (bug sweep 2026-11-12).
     * A class that cannot be asked keeps vanilla's order.
     */
    private static final ClassValue<Boolean> MOVABLE = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            for (Object[] m : ENTITY_METHODS) {
                if (!Declared.inherits(type, Entity.class, (String) m[0], (MethodType) m[1])) return Boolean.FALSE;
            }
            return Boolean.TRUE;
        }
    };

    /** True when isSunBurnTick of this mob class may ask the wet question late. */
    public static boolean movable(Class<?> mobClass) {
        boolean m = MOVABLE.get(mobClass);
        if (m && !announced) {
            announced = true;
            LOGGER.info("Bons and Furious: vanilla_sun_burn_deferred_wet_check applies (sun-burn ticks ask the wet question after the light test and the roll){}",
                    SHADOW ? " - shadow verification on" : "");
        }
        return m;
    }

    /** Shadow mode: the answer at vanilla's point next to the deferred answer. */
    public static void shadow(Object mob, boolean early, boolean late) {
        SHADOW_CHECKS.incrementAndGet();
        if (early != late) {
            long m = SHADOW_MISMATCHES.incrementAndGet();
            if (m <= 20) LOGGER.warn("Bons and Furious: vanilla_sun_burn_deferred_wet_check shadow mismatch #{}: {} was {} wet at vanilla's point and {} when asked late",
                    m, mob, early ? "" : "not", late ? "wet" : "not wet");
        }
    }
}
