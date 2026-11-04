package bons.furious.patch.l2complements_coo49;

import dev.xkmc.l2complements.content.feature.EntityFeature;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VolatileCallSite;
import java.util.ArrayList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch l2complements_attack_checks_cheap_first (L2 Complements 2.6.1, both sides). L2 Complements is All
 * Rights Reserved: none of its code is carried; this is our own logic around its unchanged calls. Ours, idea from
 * Collections Of Optimizations (l2complements.leanAttackChecks), idea text only.
 *
 * What L2 Complements does: MagicEventHandler.onLivingAttack runs for every LivingAttackEvent (every damage attempt on
 * every living entity, every burning or poison tick) and asks up to seven EntityFeature tests, each a walk over the
 * entity's equipment with an enchantment-level lookup per item (EnchantmentHelper.getEnchantmentLevel builds a slot map and
 * reads every stack's enchantment list). Six of the seven tests are ANDed with a second, cheap condition that is evaluated
 * only after the test: OWNER_PROTECTION with "the attacker is an OwnableEntity" (then its owner is the victim),
 * ENVIRONMENTAL_REJECT with "the source has no causing entity", MAGIC_REJECT / PROJECTILE_REJECT / FIRE_REJECT /
 * EXPLOSION_REJECT with "the damage type is in the magic / projectile / fire / explosion tag". Almost every attack fails
 * five or six of those cheap conditions, so the expensive tests run for nothing.
 *
 * What the switch does (a @Redirect on each of those six test calls, see AttackFeatureChecksMixin): while the six features'
 * predicate lists hold nothing but L2 Complements' EnchantmentFeaturePredicate and SlotEnchantmentFeaturePredicate (pure
 * reads of equipment and enchantment levels), the cheap condition is evaluated first (DamageSource.getEntity /
 * DamageSource.is are field and tag-set reads); when it is false the && can only be false, so the test is left out and
 * false is answered. Once another mod has added a predicate to any of the six (a Curios predicate may apply a pending
 * Curios slot update), every test runs at once, as shipped, with nothing evaluated before it. The check is made again after
 * every EntityFeature.add (EntityFeatureAddMixin; L2 Complements has nothing that removes or replaces a predicate), and its
 * answer is a call-site constant the JIT folds into the handlers. When the cheap condition is true the test runs as
 * shipped, and the shipped code then evaluates the condition again. OWNER_PROTECTION's getOwner() call still happens only
 * after the test, as shipped. INVINCIBLE (no paired condition) and the config gate are untouched. Same cancellations, same
 * calls with effects.
 *
 * Flag: -Dbons_and_furious.l2complementsAttackChecksCheapFirst=false runs every test as shipped.
 */
public final class AttackChecks {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.l2complementsAttackChecksCheapFirst", "true"));
    /** Tests left out (for probes and the harness; a plain counter, races only cost counts). */
    public static long skipped;
    private static volatile boolean announced;
    private static final Class<?> ENCHANT = load("dev.xkmc.l2complements.content.feature.EnchantmentFeaturePredicate");
    private static final Class<?> SLOT_ENCHANT = load("dev.xkmc.l2complements.content.feature.SlotEnchantmentFeaturePredicate");
    /**
     * Whether the six features are pure, as a call site the JIT folds to a constant: its target is recheck() until the first
     * test after a change, then a constant true or false. Changing the target (EntityFeature.add) throws away the compiled
     * code that folded the old answer. With another mod's predicate in place every handler compiles down to the shipped call.
     */
    private static final MethodHandle RECHECK;
    private static final VolatileCallSite PURE_SITE;
    private static final MethodHandle PURE;
    private static final Object LOCK = new Object();
    private static int version;   // EntityFeature.add calls seen, under LOCK

    static {
        try {
            RECHECK = MethodHandles.lookup().findStatic(AttackChecks.class, "recheck", MethodType.methodType(boolean.class));
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
        PURE_SITE = new VolatileCallSite(RECHECK);
        PURE = PURE_SITE.dynamicInvoker();
    }

    /** EntityFeature's predicate list, added to the enum by EntityFeatureListAccess. */
    public interface FeatureList {
        ArrayList<?> bons$l2complements_coo49Predicates();
    }

    private AttackChecks() {
    }

    private static Class<?> load(String name) {
        try {
            return Class.forName(name, false, AttackChecks.class.getClassLoader());
        } catch (Throwable t) {
            return null;
        }
    }

    /** True when the switch may leave a test out when its paired condition is false: the six features are pure, and on. */
    public static boolean fast() {
        try {
            return (boolean) PURE.invokeExact() && enabled;
        } catch (Throwable t) {
            throw new IllegalStateException(t);
        }
    }

    /** EntityFeature.add ran (EntityFeatureAddMixin): the features are checked again at the next test. */
    public static void changed() {
        synchronized (LOCK) {
            version++;
            PURE_SITE.setTarget(RECHECK);
        }
    }

    private static boolean recheck() {
        int v;
        synchronized (LOCK) {
            v = version;
        }
        boolean p = pure(EntityFeature.OWNER_PROTECTION) && pure(EntityFeature.ENVIRONMENTAL_REJECT) && pure(EntityFeature.MAGIC_REJECT)
                && pure(EntityFeature.PROJECTILE_REJECT) && pure(EntityFeature.FIRE_REJECT) && pure(EntityFeature.EXPLOSION_REJECT);
        synchronized (LOCK) {
            if (version == v) PURE_SITE.setTarget(MethodHandles.constant(boolean.class, p));   // else an add ran meanwhile: check again next time
        }
        return p;
    }

    /** True when every predicate of the feature is one of L2 Complements' two enchantment-level predicates (not cached). */
    public static boolean pure(Object feature) {
        return feature instanceof FeatureList f && pure(f.bons$l2complements_coo49Predicates());
    }

    private static boolean pure(ArrayList<?> list) {
        if (ENCHANT == null || SLOT_ENCHANT == null || list == null) return false;
        for (int i = 0, n = list.size(); i < n; i++) {
            Object p = list.get(i);
            if (p == null) return false;
            Class<?> c = p.getClass();
            if (c != ENCHANT && c != SLOT_ENCHANT) return false;
        }
        return true;
    }

    /** A test left out (its paired condition is false): the shipped && answers false. */
    public static boolean skip() {
        skipped++;
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: l2complements_attack_checks_cheap_first applies (attack immunity checks test the damage type before the equipment)");
        }
        return false;
    }
}
