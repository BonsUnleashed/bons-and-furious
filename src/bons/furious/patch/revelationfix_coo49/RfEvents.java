package bons.furious.patch.revelationfix_coo49;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Spider;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious 1.0.49, group revelationfix_coo49 (RevelationFix 4.8, the Jar-in-Jar of Goety Revelation 2.3.5; 4.7 in
 * Goety Revelation 2.3.4fix2 has the same methods): revelationfix_hurt_by_target_event_skip (exact; both sides, acts where
 * goals run: the server). RevelationFix is all rights reserved: no code of it is carried, the logic is ours.
 *
 * <p>RevelationFix's HurtByTargetGoalMixin merges three handlers into HurtByTargetGoal.canUse (m_8036_; every other tick
 * for every mob with that goal): at the head, before the toIgnoreDamage loop and before canAttack. Each builds a new
 * LivingHurtByTargetGoalEvent.CanUse from the goal's fields (timestamp, alertSameType, toIgnoreDamage, toIgnoreAlert, the
 * mob, its last attacker, the phase), posts it on MinecraftForge.EVENT_BUS, writes the event's timestamp / alertSameType /
 * toIgnoreAlert back into the goal and returns false / true from canUse on DENY / ALLOW. In the whole Goety family the only
 * listener of that event is RevelationFix's ArmorEventsHandler.hurtByTargetCanUse, which acts only in the last phase and
 * only when the goal's mob is a Spider (then it may DENY for the Spider Darkmage armour set). For every other post the event
 * comes back untouched: the write-back stores the same values (the event's getters return what its constructor took) and
 * the result is DEFAULT, so canUse goes on exactly as without the post.
 *
 * <p>The switch returns from a handler before it builds the event when the live listener census of MinecraftForge.EVENT_BUS
 * (forge_listener_guard's ListenerCensus) says the event's only listener is that handler (or there is none) and either the
 * phase is not the last one or the goal's mob is not a Spider. Any other listener, a lambda, a transformed listener class,
 * or a census that cannot answer: the event is posted as before. After the census answered "not only that handler" it is
 * asked again only every RECHECK calls (until then every event is posted, which is always right), so a handler that
 * stands aside pays one counter, not a census question.
 *
 * <p>Runtime flag (=false: off): bons_and_furious.revelationfixHurtByTargetEventSkip.
 * -Dbons_and_furious.revelationfixHurtByTargetEventSkip.count=true counts the skipped posts (offline proofs only; off,
 * the hot path has no counter). No shadow mode (the decision is the census answer plus two reads; proven offline on the
 * real merged handlers with the real bus).
 */
public final class RfEvents {
    public static final int HEAD = 0, BEFORE_IGNORE = 1, TAIL = 2;
    static final String KEY = "revelationfix_hurt_by_target_event_skip";
    static final String ARMOR_LISTENER = "com.mega.revelationfix.common.event.handler.ArmorEventsHandler|hurtByTargetCanUse|(Lcom/mega/revelationfix/api/event/entity/LivingHurtByTargetGoalEvent$CanUse;)V";
    static final String EVENT = "com.mega.revelationfix.api.event.entity.LivingHurtByTargetGoalEvent$CanUse";
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.revelationfixHurtByTargetEventSkip", "true"));
    static final boolean COUNT = Boolean.getBoolean("bons_and_furious.revelationfixHurtByTargetEventSkip.count");
    /** Offline statistics (only with COUNT): posts skipped per phase. */
    public static final long[] skipped = new long[3];
    /** Calls between two census questions while the census answers "not only that handler". */
    static final int RECHECK = 256;
    /** Calls left before the census is asked again (plain field: a stale value only delays the next question). */
    public static int standAside;

    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    private static boolean announced;

    private RfEvents() {
    }

    /** The census watch of the event class, made on first use (never while classes are being loaded). */
    private static final class Watch {
        static final Object W = make();

        private static Object make() {
            try {
                return ListenerCensusLink.watch(Class.forName(EVENT, false, RfEvents.class.getClassLoader()), new String[] {ARMOR_LISTENER});
            } catch (Throwable t) {
                return null;
            }
        }
    }

    /** True: the handler of {@code phase} may return before it builds and posts the event (see the class comment). */
    public static boolean skip(int phase, Mob mob) {
        if (!enabled || (phase == TAIL && mob instanceof Spider)) return false;
        if (standAside > 0) {
            standAside--;
            return false;
        }
        long mask = ListenerCensusLink.mask(Watch.W);
        if (mask != 0L && mask != 1L) {
            standAside = RECHECK;
            return false;
        }
        if (COUNT) skipped[phase]++;
        if (!announced) announce();
        return true;
    }

    private static synchronized void announce() {
        if (announced) return;
        announced = true;
        LOGGER.info("Bons and Furious: {}: RevelationFix's hurt-by-target events are no longer built for goals no listener reacts to", KEY);
    }
}
