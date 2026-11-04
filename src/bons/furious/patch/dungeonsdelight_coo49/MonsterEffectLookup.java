package bons.furious.patch.dungeonsdelight_coo49;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch dungeonsdelight_monster_effect_lookup (Dungeons Delight 1.2.10 / 1.3.1, both sides).
 *
 * Every "monster" effect (MonsterEffect: Tenacity, Ravenous Rush, ...) ticks on every tick (isDurationEffectTick is true)
 * and its applyEffectTick walks ALL of the holder's active effects looking for an instance whose effect is the monster
 * effect's normal variant, to swap it out. Almost always there is none and the walk does nothing.
 *
 * The switch reads the holder's activeEffects map directly first: LivingEntity keys that map by each instance's own
 * effect (every vanilla write: addEffect / forceAddEffect / load put(instance.getEffect(), instance)) and MobEffect does not
 * override equals, so "no key == normalVariant" means no instance in getActiveEffects() (the map's values) has
 * getEffect().equals(normalVariant): the original loop would run to the end and change nothing. Then the method returns at
 * once; otherwise the original runs unchanged (the swap, its removeEffect and the ConcurrentModificationException that
 * vanilla's tickEffects swallows, all as before). Applies only when the holder's class does not override getActiveEffects
 * (ClassValue): an override could hand the loop other instances. The map is read through our accessor, not hasEffect, so
 * another mod's hasEffect hook cannot change the decision. Runtime flag -Dbons_and_furious.dungeonsdelightMonsterEffectLookup=false;
 * shadow mode (.shadow=true) runs the original loop's test too and WARNs on up to 20 mismatches.
 * Dungeons Delight's licence is a custom one: no code of it is carried.
 */
public final class MonsterEffectLookup {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.dungeonsdelightMonsterEffectLookup", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.dungeonsdelightMonsterEffectLookup.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong();
    public static final AtomicLong SHADOW_MISMATCHES = new AtomicLong();
    private static volatile boolean announced;

    /** True when the class or a superclass below LivingEntity declares getActiveEffects (m_21220_). */
    private static final ClassValue<Boolean> OVERRIDES = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            try {
                for (Class<?> c = type; c != null && c != LivingEntity.class; c = c.getSuperclass()) {
                    for (Method m : c.getDeclaredMethods())
                        if (m.getName().equals("m_21220_") && m.getParameterCount() == 0) return true;
                }
                return false;
            } catch (Throwable t) {          // methods cannot be listed: the original loop runs
                return true;
            }
        }
    };

    private MonsterEffectLookup() {
    }

    /** True when the original loop over living's effects cannot find normalVariant (the method may return at once). */
    public static boolean absent(LivingEntity living, Map<MobEffect, MobEffectInstance> map, MobEffect normalVariant) {
        if (!enabled || living == null || map == null || OVERRIDES.get(living.getClass())) return false;
        if (map.containsKey(normalVariant)) return false;
        if (SHADOW) shadow(living, normalVariant);
        else if (!announced) announce();
        return true;
    }

    private static void shadow(LivingEntity living, MobEffect normalVariant) {
        SHADOW_CHECKS.incrementAndGet();
        for (MobEffectInstance i : living.m_21220_()) {
            if (i.m_19544_().equals(normalVariant)) {
                long n = SHADOW_MISMATCHES.incrementAndGet();
                if (n <= 20) LOGGER.warn("Bons and Furious: dungeonsdelight_monster_effect_lookup shadow mismatch #{}: {} holds {} under another key", n, living, i);
                return;
            }
        }
    }

    private static synchronized void announce() {
        if (announced) return;
        announced = true;
        LOGGER.info("Bons and Furious: dungeonsdelight_monster_effect_lookup: monster effects skip their swap scan when the normal effect is absent");
    }
}
