package bons.furious.patch.mob_sunburn;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.world.entity.Entity;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch vanilla_sun_burn_deferred_wet_check (Minecraft 1.21.1 with NeoForge 21.1.252; both sides, acts
 * on the server: the method returns false at once on a client level). No Minecraft code here. Idea: Gale/Leaf "Optimize
 * sun burn tick" (idea text only).
 *
 * Mob.isSunBurnTick (Zombie, AbstractSkeleton, Phantom and modded undead call it every tick in daylight) asks
 * isInWaterRainOrBubble() before its light test and its random roll, although the answer only matters when both
 * pass (the roll succeeds with probability (light - 0.4) * 2 / 30, at most 4%). That question is isInWater (a field),
 * isInRain (two Level.isRainingAt calls: Slice & Dice's bundled Atmosphere hooks isRainingAt) and isInBubbleColumn (the
 * block at the feet, through Entity's inBlockState memo on 1.21.1). The switch answers "false" in its place
 * (MobSunBurnOrderMixin, first redirect) and asks the real question just before canSeeSky (second redirect), i.e. only
 * when the light test and the roll have passed and the powder snow flags are clear: the same three conditions are combined
 * in the same "and", so the result is the same; the random roll is made exactly when vanilla makes it (it depends only on
 * the light value).
 *
 * The question is moved only for classes whose isInWater / isInWaterRainOrBubble are Entity's own (so it has no side
 * effects: field reads, effect-map and item reads, block and heightmap reads in the mob's own column), that declare
 * none of Entity's water-update methods below Entity, so the mob's tick reads its own chunk column (Entity.baseTick's
 * fluid update) before isSunBurnTick in the same tick: the chunk's UNKNOWN load ticket ends the tick the same with or
 * without the skipped reads (the argument of spawn_flyless_particle_check), and that do not override getInBlockState.
 * Every other class keeps vanilla's order. Assumption (as there): a Mob class's tick reaches Entity.baseTick.
 *
 * -Dbons_and_furious.sunBurnDeferredWetCheck=false keeps vanilla's order. -Dbons_and_furious.sunBurnDeferredWetCheck.shadow=true
 * (verification runs) also asks the question at vanilla's point and compares it with the deferred answer whenever the
 * deferred one is asked (SHADOW_CHECKS / SHADOW_MISMATCHES, first 20 logged).
 *
 * Ported to 1.21.1: 1.21.1's Entity.isInBubbleColumn reads getInBlockState(), which fills Entity's inBlockState memo when
 * it is empty: a side effect the 1.20.1 question did not have (a later getInBlockState() of the same tick, e.g.
 * LivingEntity.onClimbable in travel, would read a fresh block instead of the memo). The question is therefore moved only
 * while the memo is already set (memoSet), when isInBubbleColumn is a pure read; LivingEntity.baseTick asks
 * isInWaterRainOrBubble every tick before aiStep, which sets the memo for every dry mob, so the switch keeps its reach.
 * getInBlockState joins the methods a class must not declare below Entity.
 */
public final class SunBurnOrder {
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.sunBurnDeferredWetCheck", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.sunBurnDeferredWetCheck.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    private static volatile boolean announced;
    /** Names of Entity's methods a class must not declare below Entity for the question to move. */
    private static final String[] OWN = {"isInWaterRainOrBubble", "isInWater", "updateInWaterStateAndDoFluidPushing",
            "updateInWaterStateAndDoWaterCurrentPushing", "updateFluidHeightAndDoFluidPushing", "getInBlockState"};

    private SunBurnOrder() {
    }

    /** Per class: true when the wet question may be moved (see the class comment). */
    private static final ClassValue<Boolean> MOVABLE = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            try {
                if (type.getMethod("isInWaterRainOrBubble").getDeclaringClass() != Entity.class) return Boolean.FALSE;
                if (type.getMethod("isInWater").getDeclaringClass() != Entity.class) return Boolean.FALSE;
                if (type.getMethod("getInBlockState").getDeclaringClass() != Entity.class) return Boolean.FALSE;
                for (Class<?> c = type; c != null && c != Entity.class; c = c.getSuperclass()) {
                    for (Method m : c.getDeclaredMethods()) {
                        String n = m.getName();
                        for (String o : OWN) if (n.equals(o)) return Boolean.FALSE;
                    }
                }
                return Boolean.TRUE;
            } catch (Throwable t) {      // a signature names a missing class: cannot tell, keep vanilla's order
                return Boolean.FALSE;
            }
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

    /**
     * 1.21.1: the mob's inBlockState memo is set, so isInBubbleColumn's getInBlockState() is a pure read and moving or
     * skipping the question cannot change what the memo holds later in the tick.
     */
    public static boolean memoSet(Object inBlockState) {
        return inBlockState != null;
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
