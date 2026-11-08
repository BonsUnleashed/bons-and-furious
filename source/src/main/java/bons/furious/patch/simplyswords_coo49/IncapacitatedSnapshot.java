package bons.furious.patch.simplyswords_coo49;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.sweenus.simplyswords.api.IncapacitatingStatusEffectRegistry;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch simplyswords_incapacitated_lockfree (Simply Swords 1.70.2-1.21.1, both sides).
 *
 * Simply Swords asks IncapacitatingStatusEffectRegistry.isIncapacitated(entity) several times per living entity per tick
 * on each side (its LivingEntity, Mob and player mixins, plus ability and network code). The method is a static
 * synchronized one: every call enters the registry's class monitor (in singleplayer the client thread and the integrated
 * server thread share it), and once an addon has registered an effect it also builds an effect stream per call. Only
 * register(id) ever changes the registry's set (the field is private; the class declares register, isIncapacitated, its
 * stream lambda and registeredEffects only, which the guard fingerprints).
 *
 * The switch keeps an immutable copy of that set, republished by register at its RETURN while register still holds the
 * monitor (a volatile write), and answers isIncapacitated without the monitor: a null entity or an empty set -> false
 * (the original's first test); otherwise the entity's active effects in their own iteration order, each effect's
 * registry key (BuiltInRegistries.MOB_EFFECT.getKey of the effect holder's value, as the original's lambda asks it)
 * tested against the copy with the same Set.contains semantics, stopping at the first hit (the original's anyMatch: same
 * registry lookups, same answer). Exact: the copy always equals the set after the latest completed register, and a read
 * racing a register sees the set before or after it, as with the lock. The copy starts empty, as the set does (the mixin
 * applies when the class loads, before register can run).
 * With the runtime flag off (-Dbons_and_furious.simplyswordsIncapacitatedLockfree=false) the check runs under the class
 * monitor against the registry's own set, as before. Shadow mode (.shadow=true) runs that locked check next to every
 * lock-free answer and logs up to 20 mismatches. Simply Swords' licence (Timefall Development License) is not ours: no
 * Simply Swords code is carried; the check is our own loop.
 */
public final class IncapacitatedSnapshot {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.simplyswordsIncapacitatedLockfree", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.simplyswordsIncapacitatedLockfree.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong();
    public static final AtomicLong SHADOW_MISMATCHES = new AtomicLong();
    private static final Set<ResourceLocation> EMPTY = Set.of();
    /** The registry's set after the latest completed register (never null; HashSet.contains(null) is false like the original's). */
    private static volatile Set<ResourceLocation> snapshot = EMPTY;
    private static volatile boolean announced;

    private IncapacitatedSnapshot() {
    }

    /** Called at RETURN of register, inside its class monitor. */
    public static void publish(Set<ResourceLocation> effects) {
        snapshot = effects.isEmpty() ? EMPTY : new HashSet<>(effects);
    }

    /** The answer of IncapacitatingStatusEffectRegistry.isIncapacitated(entity). */
    public static boolean isIncapacitated(LivingEntity entity, Set<ResourceLocation> effects) {
        if (!enabled) {
            synchronized (IncapacitatingStatusEffectRegistry.class) {
                return check(entity, effects);
            }
        }
        Set<ResourceLocation> ids = snapshot;
        boolean answer = entity != null && !ids.isEmpty() && anyRegistered(entity, ids);
        if (SHADOW) shadow(entity, effects, answer);
        else if (!announced && !ids.isEmpty()) announce();
        return answer;
    }

    private static boolean check(LivingEntity entity, Set<ResourceLocation> effects) {
        return entity != null && !effects.isEmpty() && anyRegistered(entity, effects);
    }

    private static boolean anyRegistered(LivingEntity entity, Set<ResourceLocation> ids) {
        for (MobEffectInstance instance : entity.getActiveEffects()) {
            if (ids.contains(BuiltInRegistries.MOB_EFFECT.getKey(instance.getEffect().value()))) return true;
        }
        return false;
    }

    private static void shadow(LivingEntity entity, Set<ResourceLocation> effects, boolean answer) {
        boolean original;
        synchronized (IncapacitatingStatusEffectRegistry.class) {
            original = check(entity, effects);
        }
        SHADOW_CHECKS.incrementAndGet();
        if (original != answer) {
            long n = SHADOW_MISMATCHES.incrementAndGet();
            if (n <= 20) LOGGER.warn("Bons and Furious: simplyswords_incapacitated_lockfree shadow mismatch #{}: {} answered {}, the locked check {}",
                    n, entity, answer, original);
        }
    }

    private static synchronized void announce() {
        if (announced) return;
        announced = true;
        LOGGER.info("Bons and Furious: simplyswords_incapacitated_lockfree: Simply Swords' incapacitation check runs without its lock ({} effect(s) registered)",
                snapshot.size());
    }
}
