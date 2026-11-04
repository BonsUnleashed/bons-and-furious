package bons.furious.patch.chimes_coo49;

import bons.pure.config.PureConfig;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.level.Level;
import net.minecraftforge.entity.PartEntity;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious 1.0.49, group chimes_coo49: the Phantom search of Chimes 2.0.1's wind chimes and wind bells
 * (com.nick.chimes.block.entity.WindChimeBE.tick and WindBellBE.tick). Helper of the group's mixins; no code of Chimes or
 * Minecraft is carried.
 *
 * What Chimes does. Every wind chime (all four kinds share WindChimeBE.tick) and every wind bell ticks on BOTH logical sides
 * (its block's ticker ignores the side) and starts each tick with level.getEntitiesOfClass(Phantom.class, box): the chime's
 * box is its block grown by the config's horizontal / vertical repel radius (10 / 25 by default: 21 x 51 x 21 blocks), the
 * bell's by 4. The tick then loops the list only when the block entity's chunk is loaded (and, for a chime, when the block
 * is a tyable chime), and acts only on phantoms whose getTarget() is not null (a silenced tyable chime skips them all): the
 * chime plays the phantom's hurt sound from the phantom and calls setTarget(null); the bell calls setTarget(null). The
 * search is the whole cost: a walk over the 28-45 entity sections the box touches, every tick, per chime.
 *
 * Three switches, each with its own mixins (one per block entity class, all wrapping that one search call):
 *  chimes_phantom_scan_presence_gate (both sides, exact): while vanilla_entity_class_count_layer says the level holds no
 *    Phantom (registration bons_and_furious.chimes_coo49.entity_classes.json: Phantom and its subclasses, counted while
 *    Chimes is installed and this switch is on), the search is marked EMPTY: vanilla_entity_walk_skip leaves out its walk
 *    over the entity sections, which could visit no Phantom. The call still runs (the profiler's "getEntities" count,
 *    Forge's part pass, every other mod's hook on the search) and returns the new empty list it would have returned.
 *  chimes_client_phantom_scan_skip (client, exact): on a client level the search is marked UNUSED while no client-side
 *    Phantom can have a target: the loop then has nothing to act on, as with the full list. "No client-side Phantom has a
 *    target" holds while (1) no Mob.setTarget call on a client level has left a Phantom (or subclass) with a target since
 *    the game started (MobClientTargetWatchMixin reads the target field at every return of setTarget; Minecraft only sets
 *    targets in server-side AI, so in vanilla this never happens); (2) the target field is written by nothing else and
 *    getTarget is the plain read of it (ChimesPlugin's look at the merged Mob class; the field is still private, so no
 *    access transformer opened it; no mixin of another mod with priority 2000 or more came after that look); (3) no
 *    Phantom class constructed so far overrides getTarget (PhantomClassWatchMixin, at the end of Phantom's constructor).
 *    Once any of these fails, the client runs the stock search for the rest of the session. Stock's per-phantom
 *    getTarget() calls are skipped with the search: (2) makes them plain field reads.
 *  chimes_phantom_scan_interval (server, change, off by default): COO-style interval for levels that do hold phantoms:
 *    each chime and bell searches only on the ticks where (game time + its own offset from its position) is a multiple of
 *    N (value line chimes_phantom_scan_interval.value, default 4 = COO's, 1-100; 1 = every tick); on the other ticks the
 *    search is marked UNUSED and comes back empty, so a phantom that picked a target inside the box keeps it up to N-1
 *    ticks longer before the chime or bell clears it. Nothing else of the tick changes (its counters and swing state run
 *    every tick as before); nothing is saved or sent differently.
 * A mark is placed for one search on this thread and cleared in a finally block; a mark another wrapper already placed
 * makes the later one refuse (the search then uses the first one).
 *
 * Runtime flags (-D...=false turns one off): bons_and_furious.chimesPhantomScanPresenceGate,
 * .chimesClientPhantomScanSkip, .chimesPhantomScanInterval. SHADOW MODES (verification runs):
 * -Dbons_and_furious.chimesPhantomScanPresenceGate.shadow=true never marks; where the gate would act it runs the search
 * and checks the list holds no Phantom; -Dbons_and_furious.chimesClientPhantomScanSkip.shadow=true never marks; where the
 * skip would act it runs the search and checks every listed phantom's getTarget() is null. SHADOW_CHECKS /
 * SHADOW_MISMATCHES, WARN for the first 20 mismatches. The interval's shadow counter INTERVAL_DEFERRED (no switch needed:
 * with the interval's .shadow flag the off-tick searches still run) counts off-tick searches that held a targeted phantom
 * (the documented difference).
 */
public final class ChimesScans {
    public static final String PRESENCE_KEY = "chimes_phantom_scan_presence_gate";
    public static final String CLIENT_KEY = "chimes_client_phantom_scan_skip";
    public static final String INTERVAL_KEY = "chimes_phantom_scan_interval";
    public static final String PHANTOM = "net.minecraft.world.entity.monster.Phantom";

    public static volatile boolean presenceGate = flag("chimesPhantomScanPresenceGate");
    public static volatile boolean clientSkip = flag("chimesClientPhantomScanSkip");
    public static volatile boolean interval = flag("chimesPhantomScanInterval");
    public static final boolean SHADOW_PRESENCE = Boolean.getBoolean("bons_and_furious.chimesPhantomScanPresenceGate.shadow");
    public static final boolean SHADOW_CLIENT = Boolean.getBoolean("bons_and_furious.chimesClientPhantomScanSkip.shadow");
    public static final boolean SHADOW_INTERVAL = Boolean.getBoolean("bons_and_furious.chimesPhantomScanInterval.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong(), INTERVAL_DEFERRED = new AtomicLong();
    /** Statistics for probes (plain fields): searches marked by each switch. */
    public static long markedEmpty, markedClient, markedInterval;

    /** chimes_client_phantom_scan_skip: a client-side Phantom got a target (set once, never cleared). */
    public static volatile boolean clientTargetSeen;
    /** chimes_client_phantom_scan_skip: why the client skip stands down for good (null while it may act). */
    public static volatile String clientUnsafe;
    private static volatile int verified;   // 0 unknown, 1 verified, -1 refused

    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    private static volatile int announced;

    private ChimesScans() {
    }

    private static boolean flag(String name) {
        return !"false".equalsIgnoreCase(System.getProperty("bons_and_furious." + name, "true"));
    }

    /** The layer's index of Phantom, resolved on first use (the layer reads its registration files when it initialises). */
    private static final class Index {
        static final int PHANTOM_INDEX = EntityPresenceLink.trackedIndex(PHANTOM);
    }

    /** The interval value, read on first use of the interval wrapper (only reached while its switch is on). */
    private static final class Interval {
        static final int N = PureConfig.intValue(INTERVAL_KEY, 4, 1, 100);
    }

    private static void announce(int bit, String key, String what) {
        if ((announced & (1 << bit)) != 0) return;
        synchronized (ChimesScans.class) {
            if ((announced & (1 << bit)) != 0) return;
            announced |= 1 << bit;
        }
        LOGGER.info("Bons and Furious: {}: {}", key, what);
    }

    /** An entity for a log line: class and id (no getName(): nothing of the entity's state is read). */
    private static String describe(Object e) {
        return e instanceof net.minecraft.world.entity.Entity en ? en.getClass().getName() + " #" + en.m_19879_() : String.valueOf(e);
    }

    private static void mismatch(String key, String what) {
        long n = SHADOW_MISMATCHES.incrementAndGet();
        if (n <= 20) LOGGER.warn("Bons and Furious: {} SHADOW MISMATCH {}: {}", key, n, what);
    }

    // ------------------------------------------------------------------------------------------------ presence gate

    /** chimes_phantom_scan_presence_gate: the chime / bell search, marked EMPTY while the level holds no Phantom. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static List presence(Object level, Class<?> searched, Object box, Operation<List> original) {
        if (!presenceGate || searched != Phantom.class || !(level instanceof Level l)) return original.call(level, searched, box);
        int index = Index.PHANTOM_INDEX;
        if (index < 0) return original.call(level, searched, box);
        if (SHADOW_PRESENCE) {
            if (!EntityPresenceLink.none(l, index)) return original.call(level, searched, box);
            announce(0, PRESENCE_KEY, "SHADOW MODE: the chime and bell phantom searches run in full and are checked");
            List found = original.call(level, searched, box);
            SHADOW_CHECKS.incrementAndGet();
            for (Object e : found) {
                if (e instanceof PartEntity) continue;
                mismatch(PRESENCE_KEY, "the layer says the level holds no Phantom, the search found " + describe(e));
                break;
            }
            return found;
        }
        if (!EntityPresenceLink.markEmpty(PRESENCE_KEY, level, searched, index)) return original.call(level, searched, box);
        markedEmpty++;
        announce(1, PRESENCE_KEY, "wind chime and wind bell phantom searches walk no entity sections while the level holds no phantom");
        try {
            return original.call(level, searched, box);
        } finally {
            EntityPresenceLink.unmark();
        }
    }

    // ------------------------------------------------------------------------------------------------ client skip

    /** chimes_client_phantom_scan_skip: on a client level, the search is marked UNUSED while no client Phantom can have a target. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static List clientSkip(Object level, Class<?> searched, Object box, Operation<List> original) {
        if (!clientSkip || searched != Phantom.class || !(level instanceof Level l) || !l.f_46443_ || !noClientTargets())
            return original.call(level, searched, box);
        if (SHADOW_CLIENT) {
            announce(2, CLIENT_KEY, "SHADOW MODE: the client's chime and bell phantom searches run in full and are checked");
            List found = original.call(level, searched, box);
            SHADOW_CHECKS.incrementAndGet();
            for (Object e : found) {
                if (e instanceof Mob m && m.m_5448_() != null) {
                    mismatch(CLIENT_KEY, "a client-side phantom has a target: " + describe(e) + " -> " + describe(m.m_5448_()));
                    break;
                }
            }
            return found;
        }
        if (!EntityPresenceLink.markUnused(CLIENT_KEY, level, searched)) return original.call(level, searched, box);
        markedClient++;
        announce(3, CLIENT_KEY, "the client skips the wind chime and wind bell phantom searches (no client-side phantom has a target)");
        try {
            return original.call(level, searched, box);
        } finally {
            EntityPresenceLink.unmark();
        }
    }

    /** True while every condition of the client skip holds (see the class comment); checked once, then two volatile reads. */
    public static boolean noClientTargets() {
        if (clientTargetSeen || clientUnsafe != null) return false;
        int v = verified;
        if (v == 0) v = verify();
        return v > 0;
    }

    private static synchronized int verify() {
        if (verified != 0) return verified;
        String why = ChimesPlugin.mobVerdict;
        if (why == null) why = "the look at the Mob class did not run";
        if (why.isEmpty()) why = fieldWidened();
        if (why.isEmpty()) why = laterMobMixins();
        if (!why.isEmpty()) {
            standDown(why);
            verified = -1;
        } else {
            verified = 1;
        }
        return verified;
    }

    /** "" while Mob's target field is still private (no access transformer opened it to other classes). */
    public static String fieldWidened() {
        try {
            int mod = Mob.class.getDeclaredField("f_21362_").getModifiers();
            return Modifier.isPrivate(mod) ? "" : "Mob's target field is not private here (" + Modifier.toString(mod) + ")";
        } catch (Throwable t) {
            return "Mob's target field could not be inspected (" + t + ")";
        }
    }

    /** "" when no mixin of another mod with priority at or above the watch mixin's was applied to Mob. */
    public static String laterMobMixins() {
        try {
            int ours = ChimesPlugin.watchPriority;
            for (org.spongepowered.asm.mixin.extensibility.IMixinInfo mi : org.spongepowered.asm.mixin.transformer.ClassInfo
                    .forName("net/minecraft/world/entity/Mob").getAppliedMixins()) {
                if (mi.getClassName().startsWith("bons.furious.mixin.chimes_coo49.")) continue;
                if (mi.getPriority() >= ours) return "the mixin " + mi.getClassName() + " (priority " + mi.getPriority() + ") was applied to Mob after the look at its target field";
            }
            return "";
        } catch (Throwable t) {
            return "the mixins applied to Mob could not be listed (" + t + ")";
        }
    }

    /** The client skip stops for the rest of the session (one INFO line). */
    static void standDown(String why) {
        boolean first;
        synchronized (ChimesScans.class) {
            first = clientUnsafe == null && !clientTargetSeen;
            if (clientUnsafe == null) clientUnsafe = why;
        }
        if (first) LOGGER.info("Bons and Furious: {} steps aside for the rest of the session: {}; the client searches as Chimes does", CLIENT_KEY, why);
    }

    /** MobClientTargetWatchMixin, at every return of Mob.setTarget: {@code target} is the target field's value now. */
    public static void targetSet(Mob mob, Object target) {
        if (target == null || clientTargetSeen || !(mob instanceof Phantom)) return;
        Level level = mob.m_9236_();
        if (level == null || !level.f_46443_) return;
        boolean first;
        synchronized (ChimesScans.class) {
            first = !clientTargetSeen && clientUnsafe == null;
            clientTargetSeen = true;
        }
        if (first) LOGGER.info("Bons and Furious: {} steps aside for the rest of the session: a client-side phantom got a target ({}); the client searches as Chimes does",
                CLIENT_KEY, mob.getClass().getName());
    }

    /** PhantomClassWatchMixin, at the end of Phantom's constructor (both sides). */
    public static void phantomConstructed(Class<?> type) {
        if (clientUnsafe == null && !PLAIN_TARGET.get(type)) standDown("the phantom class " + type.getName() + " overrides getTarget");
    }

    /** True when no class from {@code type} up to (not including) Mob declares getTarget (m_5448_) other than as a bridge. */
    public static final ClassValue<Boolean> PLAIN_TARGET = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            for (Class<?> c = type; c != null && c != Mob.class; c = c.getSuperclass()) {
                if (declares(c, "m_5448_")) return false;
            }
            return true;
        }
    };

    /** c itself declares a non-bridge method of that name and no parameters; an inspection failure counts as declared. */
    static boolean declares(Class<?> c, String name) {
        try {
            for (java.lang.reflect.Method m : c.getDeclaredMethods())
                if (m.getName().equals(name) && m.getParameterCount() == 0 && !m.isBridge()) return true;
            return false;
        } catch (Throwable t) {
            return true;
        }
    }

    // ------------------------------------------------------------------------------------------------ interval (change)

    /** chimes_phantom_scan_interval: on a server level, the search runs only on this block's scan ticks. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static List interval(Object level, BlockPos pos, Class<?> searched, Object box, Operation<List> original) {
        if (!interval || searched != Phantom.class || !(level instanceof Level l) || l.f_46443_ || pos == null)
            return original.call(level, searched, box);
        int n = Interval.N;
        if (n <= 1 || scanTick(l.m_46467_(), pos, n)) return original.call(level, searched, box);
        if (SHADOW_INTERVAL) {
            List found = original.call(level, searched, box);
            for (Object e : found) {
                if (e instanceof Mob m && m.m_5448_() != null) {
                    INTERVAL_DEFERRED.incrementAndGet();
                    break;
                }
            }
            return found;
        }
        if (!EntityPresenceLink.markUnused(INTERVAL_KEY, level, searched)) return original.call(level, searched, box);
        markedInterval++;
        announce(4, INTERVAL_KEY, "wind chimes and wind bells search for phantoms every " + n + " ticks (each with its own offset)");
        try {
            return original.call(level, searched, box);
        } finally {
            EntityPresenceLink.unmark();
        }
    }

    /** The block's scan ticks: (game time + offset) is a multiple of n; the offset comes from the position (0..n-1). */
    public static boolean scanTick(long gameTime, BlockPos pos, int n) {
        return Math.floorMod(gameTime + offset(pos, n), (long) n) == 0;
    }

    /** The block's own offset in 0..n-1, from its packed position (so neighbours spread over the n ticks). */
    public static int offset(BlockPos pos, int n) {
        long p = pos.m_121878_();
        int h = (int) (p ^ (p >>> 32)) * 0x9E3779B9;
        return Math.floorMod(h ^ (h >>> 16), n);
    }
}
