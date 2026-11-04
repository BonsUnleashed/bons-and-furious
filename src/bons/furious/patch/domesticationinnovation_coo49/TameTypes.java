package bons.furious.patch.domesticationinnovation_coo49;

import com.github.alexthe668.domesticationinnovation.server.entity.ModifedToBeTameable;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.world.entity.LivingEntity;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch domesticationinnovation_tame_type_memo (Domestication Innovation 1.7.1, both sides).
 *
 * TameableUtils.isTamed(entity) is asked for every living entity on every tick (CommonProxy.onLivingUpdate and the pet
 * layers); couldBeTamed, getOwnerOf and getOwnerUUIDOf come next to it. For an entity that can never be tamed each of them
 * ends in a failing `instanceof ModifedToBeTameable`. ModifedToBeTameable is an interface (Domestication Innovation's
 * mixin duck), and a failing interface test scans the whole secondary-supers array of the entity's class on every call
 * (HotSpot only caches successful tests). In a large pack an entity class carries dozens to hundreds of mixin duck
 * interfaces. Measured in a large pack: isTamed 0.54 % self / 0.65 % inclusive of the integrated server thread in one
 * profile and 0.22 % leaf in another.
 *
 * The switch answers those four tests from a per-class cache (ClassValue): `instanceof` is "the object is not null and
 * ModifedToBeTameable is assignable from its class", and a loaded class's supertypes never change, so the cached answer is
 * the test's answer for every object, forever. The class tests before it (Axolotl, Fox, Rabbit, Frog, TamableAnimal) and
 * everything else in the four methods are unchanged. Identical results for every input.
 *
 * The cache lookup costs a few nanoseconds itself, which is more than the failing test costs while the entity classes
 * carry few interfaces (a small pack). So the cache is used only when LivingEntity and its superclasses carry at least
 * MIN_INTERFACES distinct interfaces (counted once, when the switch first acts; mixins have long been applied then);
 * below that the original test runs, through a branch the JIT removes. Both paths give the same answer.
 * Runtime flag -Dbons_and_furious.domesticationTameTypeMemo=false (the original test); the threshold is
 * -Dbons_and_furious.domesticationTameTypeMemo.minInterfaces=N (default 56, 0 = always the cache; measured crossover: a failing test over about 56 to 62 interfaces costs what the cache lookup costs). Shadow mode
 * (.shadow=true) also runs the original test on every cached answer and logs up to 20 mismatches. Domestication
 * Innovation is LGPL-3.0; no code of it is carried.
 */
public final class TameTypes {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.domesticationTameTypeMemo", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.domesticationTameTypeMemo.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong();
    public static final AtomicLong SHADOW_MISMATCHES = new AtomicLong();
    public static final int MIN_INTERFACES = Integer.getInteger("bons_and_furious.domesticationTameTypeMemo.minInterfaces", 56);
    /** Distinct interfaces (super-interfaces included) of LivingEntity and its superclasses, counted at first use. */
    public static final int LIVING_INTERFACES = interfaceCount(LivingEntity.class);
    /** The cache is worth its lookup only when a failing interface test has a long list to scan. */
    public static final boolean ACTIVE = LIVING_INTERFACES >= MIN_INTERFACES;

    static {
        LOGGER.info(ACTIVE
                ? "Bons and Furious: domesticationinnovation_tame_type_memo: Domestication Innovation's tameable-type test is answered per class (living entities carry {} interfaces)"
                : "Bons and Furious: domesticationinnovation_tame_type_memo: living entities carry only {} interfaces (fewer than {}); Domestication Innovation's own test is kept, it is as fast here",
                LIVING_INTERFACES, MIN_INTERFACES);
    }

    private static final ClassValue<Boolean> DUCK = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            return ModifedToBeTameable.class.isAssignableFrom(type);
        }
    };

    private TameTypes() {
    }

    /** Distinct interfaces, super-interfaces included, of the class and its superclasses. */
    public static int interfaceCount(Class<?> type) {
        Set<Class<?>> seen = new HashSet<>();
        for (Class<?> c = type; c != null; c = c.getSuperclass()) add(c.getInterfaces(), seen);
        return seen.size();
    }

    private static void add(Class<?>[] interfaces, Set<Class<?>> seen) {
        for (Class<?> i : interfaces) if (seen.add(i)) add(i.getInterfaces(), seen);
    }

    /** `entity instanceof ModifedToBeTameable`, from the per-class cache. */
    public static boolean isDuck(Object entity, Operation<Boolean> original) {
        if (!ACTIVE || !enabled) return original.call(entity);
        boolean answer = entity != null && DUCK.get(entity.getClass());
        if (SHADOW) shadow(entity, answer);
        return answer;
    }

    private static void shadow(Object entity, boolean answer) {
        SHADOW_CHECKS.incrementAndGet();
        if ((entity instanceof ModifedToBeTameable) != answer) {
            long n = SHADOW_MISMATCHES.incrementAndGet();
            if (n <= 20) LOGGER.warn("Bons and Furious: domesticationinnovation_tame_type_memo shadow mismatch #{}: {} answered {}", n,
                    entity.getClass().getName(), answer);
        }
    }
}
