package bons.furious.patch.adamsarsplus_coo49;

import bons.pure.config.PureConfig;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious 1.0.49, group adamsarsplus_coo49 (Adam's Ars Plus 1.20.1-5.0.3). Helper of the group's mixins; Adam's
 * Ars Plus is LGPL-3.0, no code of it is carried (the logic here is ours).
 *
 * <p>adamsarsplus_disruption_lean_walk (exact, both sides): DisruptionEffect.applyEffectTick walks the victim's effects by
 * index and, for every index, copies getActiveEffects() into a new list through a stream (twice when the effect is
 * beneficial: once to test it, once to remove it). The lean walk visits the same index in the same order through a fresh
 * iterator over the same live collection (stream().toList() of a HashMap view has its iteration order), tests the same
 * effect and calls the same removeEffect, re-reading the size before every step exactly as the original loop: the same
 * removal calls in the same order (including the effects the shrinking index skips, and the ConcurrentModificationException
 * that tickEffects swallows afterwards), without the list copies. Only for entity classes that inherit LivingEntity's
 * getActiveEffects (a plain view); others run the original.
 *
 * <p>adamsarsplus_disruption_clean_purge (gameplay fix, off by default): the original removes effects from inside
 * LivingEntity.tickEffects' own loop over the same map; the next step of that loop then throws a
 * ConcurrentModificationException, which tickEffects swallows, so the victim's later effects skip that tick; and removing by
 * index while the list shrinks skips about every other beneficial effect. With the switch, the Disruption tick only marks
 * the victim (server side; on the client it does nothing, the server's removals arrive by packet), and right after
 * tickEffects' loop (before its effects-changed update, so visibility and particle colour follow in the same tick) every
 * beneficial effect is removed through removeEffect (Forge's MobEffectEvent.Remove is posted for each, as before). What a
 * player can notice: Disruption strips every beneficial effect at once, and the victim's other effects keep counting down
 * that tick. Entity classes that replace tickEffects run the original.
 *
 * <p>adamsarsplus_flame_aura_interval (trade-off, off by default): FlameDeityAuraEffect.applyEffectTick searches every living
 * entity in a cube of 18 * (1 + amplifier) blocks around the holder on every tick, on both sides, to give the ones without it
 * Marked For Cremation (80 ticks). With the switch the sweep runs on one tick in N (the value, default 10), on the holder's
 * own phase (tick count + entity id); a mob entering the aura, or whose mark ran out, is marked up to N - 1 ticks later.
 *
 * <p>adamsarsplus_flame_aura_server_only (trade-off, off by default, client): the client copy of the sweep gives client-only
 * marks (other entities' effects are not sent to the holder's client as they change; Adam's MixinEntity then answers
 * fireImmune() false for them, so burning fire-immune mobs show flames there). With the switch the client skips the sweep:
 * those mobs burn without visible flames on the holder's screen; nothing changes on the server.
 *
 * <p>Runtime flags (=false: off): bons_and_furious.adamsarsplusDisruptionLeanWalk, .adamsarsplusDisruptionCleanPurge,
 * .adamsarsplusFlameAuraInterval, .adamsarsplusFlameAuraServerOnly; the interval value: the
 * "adamsarsplus_flame_aura_interval.value" line or -Dbons_and_furious.value.adamsarsplus_flame_aura_interval=N.
 */
public final class AapEffects {
    static final String LEAN_KEY = "adamsarsplus_disruption_lean_walk", PURGE_KEY = "adamsarsplus_disruption_clean_purge",
            INTERVAL_KEY = "adamsarsplus_flame_aura_interval", CLIENT_KEY = "adamsarsplus_flame_aura_server_only";
    public static volatile boolean leanWalk = flag("adamsarsplusDisruptionLeanWalk");
    public static volatile boolean cleanPurge = flag("adamsarsplusDisruptionCleanPurge");
    public static volatile boolean auraInterval = flag("adamsarsplusFlameAuraInterval");
    public static volatile boolean auraServerOnly = flag("adamsarsplusFlameAuraServerOnly");
    /** Statistics (plain fields): lean walks, purges marked / run, aura sweeps skipped (interval, client). */
    public static long leanWalks, purgesMarked, purgesRun, sweepsSkipped, clientSweepsSkipped;

    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    private static volatile boolean announcedLean, announcedPurge, announcedInterval, announcedClient;

    /** The interval, read once on the switch-on path. */
    static final class Interval {
        static final int N = PureConfig.intValue("adamsarsplus_flame_aura_interval", 10, 1, 80);
    }

    private AapEffects() {
    }

    private static boolean flag(String name) {
        return !"false".equalsIgnoreCase(System.getProperty("bons_and_furious." + name, "true"));
    }

    /** Entity classes whose getActiveEffects() is LivingEntity's own (the live values() view). */
    private static final ClassValue<Boolean> PLAIN_EFFECTS = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> c) {
            try {
                return c.getMethod("m_21220_").getDeclaringClass() == LivingEntity.class;
            } catch (Throwable t) {
                return false;
            }
        }
    };

    /** Entity classes that keep LivingEntity's tickEffects (the clean purge's tail runs inside it). */
    private static final ClassValue<Boolean> OWN_TICK = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> c) {
            try {
                for (Class<?> k = c; k != null && k != LivingEntity.class; k = k.getSuperclass()) {
                    for (java.lang.reflect.Method m : k.getDeclaredMethods())
                        if (m.getName().equals("m_21217_") && m.getParameterCount() == 0) return false;
                }
                return true;
            } catch (Throwable t) {
                return false;
            }
        }
    };

    /** The i-th element of the collection's iteration order (a fresh iterator: nothing is held across removeEffect). */
    private static MobEffectInstance nth(Collection<MobEffectInstance> c, int i) {
        Iterator<MobEffectInstance> it = c.iterator();
        for (int k = 0; k < i; k++) it.next();
        return it.next();
    }

    /** HEAD of DisruptionEffect.applyEffectTick (lean walk): true = handled here (the original then returns at once). */
    public static boolean leanDisruption(LivingEntity e) {
        if (!leanWalk || e == null || !PLAIN_EFFECTS.get(e.getClass())) return false;
        if (!announcedLean) {
            announcedLean = true;
            LOGGER.info("Bons and Furious: {}: Disruption walks the victim's effects without copying the effect list per step", LEAN_KEY);
        }
        leanWalks++;
        for (int i = 0; i < e.m_21220_().size(); ++i) {
            if (!nth(e.m_21220_(), i).m_19544_().m_19486_()) continue;
            e.m_21195_(nth(e.m_21220_(), i).m_19544_());
        }
        return true;
    }

    /** HEAD of DisruptionEffect.applyEffectTick (clean purge): true = the original returns at once. */
    public static boolean purgeLater(LivingEntity e) {
        if (!cleanPurge || !(e instanceof AapPurgeTarget target) || !OWN_TICK.get(e.getClass())) return false;
        if (!announcedPurge) {
            announcedPurge = true;
            LOGGER.info("Bons and Furious: {}: Disruption removes every beneficial effect after the victim's effect loop, server side", PURGE_KEY);
        }
        if (e.m_9236_().f_46443_) return true;           // client: the server's removals arrive as packets
        target.bons$adamsarsplusMarkPurge();
        purgesMarked++;
        return true;
    }

    /** After LivingEntity.tickEffects' loop, for a marked victim: every beneficial effect, through removeEffect. */
    public static void purgeNow(LivingEntity e) {
        purgesRun++;
        List<MobEffect> beneficial = new ArrayList<>();
        for (MobEffectInstance i : e.m_21220_()) if (i.m_19544_().m_19486_()) beneficial.add(i.m_19544_());
        for (MobEffect m : beneficial) e.m_21195_(m);
    }

    /** HEAD of FlameDeityAuraEffect.applyEffectTick (interval): true = this tick's sweep is skipped. */
    public static boolean skipAura(LivingEntity holder) {
        if (!auraInterval || holder == null) return false;
        int n = Interval.N;
        if (n <= 1 || Math.floorMod(holder.f_19797_ + holder.m_19879_(), n) == 0) return false;
        if (!announcedInterval) {
            announcedInterval = true;
            LOGGER.info("Bons and Furious: {}: the Flame Deity aura sweeps every {} ticks", INTERVAL_KEY, n);
        }
        sweepsSkipped++;
        return true;
    }

    /** HEAD of FlameDeityAuraEffect.applyEffectTick (client): true = the client's sweep is skipped. */
    public static boolean skipClientAura(LivingEntity holder) {
        if (!auraServerOnly || holder == null || !holder.m_9236_().f_46443_) return false;
        if (!announcedClient) {
            announcedClient = true;
            LOGGER.info("Bons and Furious: {}: the Flame Deity aura sweep runs on the server only", CLIENT_KEY);
        }
        clientSweepsSkipped++;
        return true;
    }
}
