package bons.furious.patch.effect_remove_skip;

import java.lang.reflect.Method;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.LivingEntity;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch forge_absent_effect_remove_skip (Forge 47.4.28, both sides, since 1.0.49; kind change, OFF by
 * default). Idea: Collections Of Optimizations (skarriermobs.leanResisteelSwordScan), idea text only. Ours entirely.
 *
 * Forge's LivingEntity.removeEffect(MobEffect) (m_21195_) first posts MobEffectEvent.Remove (built with getEffect(effect),
 * cancellable), then removeEffectNoUpdate(effect) (activeEffects.remove) and onEffectRemoved when an instance came back. For
 * an effect the entity does not have, the call returns false whatever the listeners do, and nothing else changes; only the
 * event post (an event object, the effect lookup, every listener of MobEffectEvent.Remove) is work. Mods that strip an
 * effect from every entity on a timer (Skarrier Mobs' ResisteelSwordEffectProcedure: every living entity every 30 ticks in
 * 1.1.2, every tick in 1.0.6) pay that per entity.
 *
 * With the switch on: when the entity holds no instance of the effect (activeEffects.containsKey false) and its class
 * does not replace removeEffectNoUpdate (m_6234_, which the original calls even for an absent effect), removeEffect returns
 * false without posting the event. Return value and effect map are the same as before.
 * Trade-off (off by default): mods that listen for effect removal are no longer told when something asks to remove an
 * effect the entity does not have.
 *
 * -Dbons_and_furious.forgeAbsentEffectRemoveSkip=false (emergency off) posts the event again.
 */
public final class AbsentEffectRemove {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.forgeAbsentEffectRemoveSkip", "true"));
    /** Removals answered without the event (statistics; plain, racy). */
    public static long skipped;
    private static volatile boolean announced;

    /** True for entity classes that inherit LivingEntity's removeEffectNoUpdate. */
    static final ClassValue<Boolean> PLAIN = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            try {
                Method m = type.getMethod("m_6234_", MobEffect.class);
                return m.getDeclaringClass() == LivingEntity.class;
            } catch (NoSuchMethodException | SecurityException | LinkageError e) {
                return false;
            }
        }
    };

    private AbsentEffectRemove() {
    }

    public static boolean plain(Class<?> type) {
        return PLAIN.get(type);
    }

    public static void skippedOne() {
        skipped++;
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: forge_absent_effect_remove_skip applies (removing an effect an entity does not have no longer posts MobEffectEvent.Remove)");
        }
    }
}
