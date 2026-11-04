package bons.furious.patch.revelationfix_fluid_coo49;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious 1.0.49, group revelationfix_fluid_coo49 (RevelationFix 4.8, the Jar-in-Jar of Goety Revelation 2.3.5;
 * 4.7 in Goety Revelation 2.3.4fix2 has the same methods): revelationfix_fluid_walk_scan (exact; both sides, acts where
 * entities move). RevelationFix is all rights reserved: no code of it is carried, the logic is ours.
 *
 * <p>RevelationFix's EntityMixin.fluidCollision (@ModifyVariable in Entity.move, right after the collide call: every move
 * of every entity) returns the movement at once for an entity that is not living or moves up. Otherwise, on every move, it
 * builds a table of twelve offsets (thirteen arrays) and, for each, a block position and the fluid there: the entity's
 * own block column and its west / north neighbours, at its feet and one below. For a fluid whose surface touches the
 * entity's box (inflated by 0.5) above the movement's height it keeps the highest one; with none found it returns the
 * movement. With one found it posts a new StandOnFluidEvent on MinecraftForge.EVENT_BUS; only when a listener cancels it
 * does it set fallDistance to 0, set the entity on the ground and return a new movement at the fluid's height; otherwise it
 * returns the movement it was given.
 *
 * <p>In the whole Goety family the event's only listener is RevelationFix's ArmorEventsHandler.standOnFluidEventEvent,
 * which cancels only for a player wearing Apocalyptium boots who is not sneaking. So while the live listener census of
 * MinecraftForge.EVENT_BUS (forge_listener_guard's ListenerCensus) says the event has no listener (0) or only that one
 * (1), and the entity is not a player, the scan can only end in "return the movement it was given": the switch lets the
 * handler take its own early return of that same movement before the scan (the scan only reads: fluid lookups of the
 * entity's own block and its west / north and lower neighbours). A player, any other listener (a lambda, another class,
 * a listener of a parent event class), a transformed listener class or a census that cannot answer: the scan runs as
 * before. A player is told apart before the census is asked; after the census answered "not only that listener" it is
 * asked again only every RECHECK calls (until then the scan runs, which is always right), so a running scan pays one type
 * test or one counter.
 *
 * <p>Runtime flag (=false: off): bons_and_furious.revelationfixFluidWalkScan.
 * -Dbons_and_furious.revelationfixFluidWalkScan.count=true counts the skipped scans (offline proofs only). No shadow mode
 * (the decision is the census answer plus one type test; proven offline on the real merged handler with the real bus).
 */
public final class RfFluidWalk {
    static final String KEY = "revelationfix_fluid_walk_scan";
    static final String ARMOR_LISTENER = "com.mega.revelationfix.common.event.handler.ArmorEventsHandler|standOnFluidEventEvent|(Lcom/mega/revelationfix/api/event/entity/StandOnFluidEvent;)V";
    static final String EVENT = "com.mega.revelationfix.api.event.entity.StandOnFluidEvent";
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.revelationfixFluidWalkScan", "true"));
    static final boolean COUNT = Boolean.getBoolean("bons_and_furious.revelationfixFluidWalkScan.count");
    /** Offline statistics (only with COUNT): scans skipped. */
    public static long skipped;
    /** Calls between two census questions while the census answers "not only RevelationFix's listener". */
    static final int RECHECK = 256;
    /** Calls left before the census is asked again (plain field: a stale value only delays the next question). */
    public static int standAside;

    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    private static boolean announced;

    private RfFluidWalk() {
    }

    /** The census watch of the event class, made on first use (never while classes are being loaded). */
    private static final class Watch {
        static final Object W = make();

        private static Object make() {
            try {
                return ListenerCensusLink.watch(Class.forName(EVENT, false, RfFluidWalk.class.getClassLoader()), new String[] {ARMOR_LISTENER});
            } catch (Throwable t) {
                return null;
            }
        }
    }

    /**
     * Called at RevelationFix's "moves up" test for a living entity that does not move up: true = the handler may return
     * the movement it was given at once (see the class comment).
     */
    public static boolean skip(Entity entity) {
        if (!enabled || entity instanceof Player) return false;
        if (standAside > 0) {
            standAside--;
            return false;
        }
        long mask = ListenerCensusLink.mask(Watch.W);
        if (mask != 0L && mask != 1L) {
            standAside = RECHECK;
            return false;
        }
        if (COUNT) skipped++;
        if (!announced) announce();
        return true;
    }

    private static synchronized void announce() {
        if (announced) return;
        announced = true;
        LOGGER.info("Bons and Furious: {}: RevelationFix's walk-on-fluid scan no longer runs for entities no listener can stand on fluid", KEY);
    }
}
