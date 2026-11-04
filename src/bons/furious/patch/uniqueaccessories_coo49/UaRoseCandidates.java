package bons.furious.patch.uniqueaccessories_coo49;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch uniqueaccessories_rose_cleanup_candidates (Unique Accessories 1.3.3, All Rights Reserved; server
 * side). Ours; no Unique Accessories code is carried. Idea: Collections Of Optimizations (uniqueaccessories.skipRoseCleanupScan),
 * idea text only.
 *
 * <p>RoseOfTemptationEvents.cleanupAroundPlayer runs for every player on every server tick: it reads the player's hands
 * and Curios slots for a Rose of Temptation (isHoldingOrEquipped, whose answer it does not use), then scans every mob in a
 * box of 32 blocks around the player, reads each mob's ForgeData "ua_rose_applied_until" (creating an empty ForgeData on
 * mobs that have none) and, for a mob whose value is above zero and in the past, removes the Rose speed modifiers and the
 * value. Only RoseOfTemptationEvents.applyUniformSpeedWithTTL writes that value (a census of the jar: no other class
 * names it), and Entity.load can bring it back with a mob (chunk loading, /data, dimension copies).
 *
 * <p>This keeps, per ServerLevel, the mobs that may carry the value: added after every applyUniformSpeedWithTTL (a mob
 * already in its level's set costs two field reads: its mark and the level's set) and after an Entity.load that leaves the
 * value above zero, and pruned
 * when checked (removed from the world, in another level, or the value gone or not above zero). When the player's level
 * holds no such mob, the scan can find nothing to act on, so the call returns at once, provided the dead Curios read would
 * only read (CuriosPresence.quiet); otherwise it runs as shipped. Entries never block exactness: an entry that should be
 * gone only makes the scan run. Above 4096 entries in one level the level is no longer gated (always scanned). The last
 * live candidate found is kept as a hint and checked first, without the lock, so while a lured mob is around the shipped
 * scan starts after one check (a hint can only send the call to the shipped scan).
 *
 * <p>Exact variant: the left-out scan no longer gives the mobs near a player an empty ForgeData compound (the one internal
 * difference). Runtime flag: -Dbons_and_furious.uniqueaccessoriesRoseCleanupCandidates=false (the index is kept either way,
 * so turning it on later stays exact).
 */
public final class UaRoseCandidates {
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.uniqueaccessoriesRoseCleanupCandidates", "true"));
    static final String TAG = "ua_rose_applied_until";
    static final int CAP = 4096;
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    private static final MethodHandle QUIET = find("bons.furious.patch.curios_presence.CuriosPresence", "quiet",
            MethodType.methodType(boolean.class, LivingEntity.class));
    private static volatile boolean announced;
    /** Statistics (plain, racy): cleanups left out. */
    public static long skipped;

    /** The per-level candidate set (a field UaServerLevelRoseMixin adds to ServerLevel). */
    public interface LevelHolder {
        Object bons$uniqueaccessoriesCoo49RoseCandidates();

        void bons$uniqueaccessoriesCoo49SetRoseCandidates(Object set);
    }

    /** Entity's own Forge field, read without creating it (UaRoseDataAccessor on Entity). */
    public interface Data {
        CompoundTag bons$uniqueaccessoriesCoo49RoseData();
    }

    /** The candidate set the entity is in, or null (a field UaEntityLoadRoseMixin adds to Entity; written under that set's lock). */
    public interface Mark {
        Object bons$uniqueaccessoriesCoo49RoseMark();

        void bons$uniqueaccessoriesCoo49SetRoseMark(Object set);
    }

    /** A level's candidates; overflow = no longer gated. */
    static final class Candidates {
        final Set<Mob> mobs = Collections.newSetFromMap(new IdentityHashMap<>());
        boolean overflow;
        /** The last live candidate found: read without the lock, it can only send the call to the shipped scan. */
        volatile Mob live;
    }

    private UaRoseCandidates() {
    }

    private static MethodHandle find(String owner, String name, MethodType type) {
        try {
            return MethodHandles.lookup().findStatic(Class.forName(owner, false, UaRoseCandidates.class.getClassLoader()), name, type);
        } catch (ReflectiveOperationException | LinkageError e) {
            LOGGER.warn("Bons and Furious: uniqueaccessories_rose_cleanup_candidates cannot reach {}.{} ({}); players with a Curios inventory run the stock cleanup", owner, name, e.toString());
            return null;
        }
    }

    static boolean quiet(LivingEntity entity) {
        if (QUIET == null) return false;
        try {
            return (boolean) QUIET.invokeExact(entity);
        } catch (Throwable t) {
            return false;
        }
    }

    /** The boost value without creating ForgeData (0 when absent). */
    static long value(Mob mob) {
        CompoundTag tag = mob instanceof Data d ? d.bons$uniqueaccessoriesCoo49RoseData() : mob.getPersistentData();
        return tag == null ? 0L : tag.m_128454_(TAG);                         // getLong
    }

    private static Candidates of(Level level, boolean create) {
        if (!(level instanceof LevelHolder h)) return null;
        Object c = h.bons$uniqueaccessoriesCoo49RoseCandidates();
        if (c == null && create) {
            c = new Candidates();
            h.bons$uniqueaccessoriesCoo49SetRoseCandidates(c);
        }
        return (Candidates) c;
    }

    /** Called under c's lock: the mob is in c (mark it) or no longer (clear the mark when it names c). */
    private static void mark(Mob mob, Candidates c, boolean in) {
        if (!(mob instanceof Mark k)) return;
        if (in) k.bons$uniqueaccessoriesCoo49SetRoseMark(c);
        else if (k.bons$uniqueaccessoriesCoo49RoseMark() == c) k.bons$uniqueaccessoriesCoo49SetRoseMark(null);
    }

    private static void add(Mob mob) {
        Candidates c = of(mob.m_9236_(), true);
        if (c == null) return;
        synchronized (c) {
            if (c.overflow) return;
            c.mobs.add(mob);
            mark(mob, c, true);
            if (c.mobs.size() > CAP) {
                c.overflow = true;
                for (Mob m : c.mobs) mark(m, c, false);
                c.mobs.clear();
                LOGGER.info("Bons and Furious: uniqueaccessories_rose_cleanup_candidates: more than {} lured mobs in one level; that level's cleanup scans run as shipped from now on", CAP);
            }
        }
    }

    /**
     * After applyUniformSpeedWithTTL(mob, now): the mob may carry the value now. A mob whose mark names its level's set is in
     * it already (boost, prune and the mark of one level's mobs all happen on the thread that ticks its players): two field
     * reads.
     */
    public static void boosted(Mob mob) {
        if (mob == null) return;
        if (mob instanceof Mark k) {
            Object in = k.bons$uniqueaccessoriesCoo49RoseMark();
            if (in != null && in == of(mob.m_9236_(), false)) return;
        }
        add(mob);
    }

    /** After Entity.load: a mob that came back with the value. */
    public static void loaded(Entity entity) {
        if (entity instanceof Mob mob && mob instanceof Data d) {
            CompoundTag tag = d.bons$uniqueaccessoriesCoo49RoseData();
            if (tag != null && tag.m_128454_(TAG) > 0L) add(mob);
        }
    }

    /** True when cleanupAroundPlayer(level, player, radius) cannot act on anything (see the class comment). */
    public static boolean skipCleanup(ServerLevel level, Player player) {
        if (!enabled) return false;
        Candidates c = of(level, true);
        if (c == null) return false;
        Mob hint = c.live;               // a lured mob is still there (the common case while one is): scan at once, no lock
        if (hint != null && !hint.m_213877_() && hint.m_9236_() == level && value(hint) > 0L) return false;
        synchronized (c) {
            if (c.overflow) return false;
            c.live = null;
            if (!c.mobs.isEmpty()) {
                for (Iterator<Mob> it = c.mobs.iterator(); it.hasNext(); ) {
                    Mob m = it.next();
                    if (m.m_213877_() || m.m_9236_() != level || value(m) <= 0L) {      // isRemoved, level, value
                        it.remove();
                        mark(m, c, false);
                        continue;
                    }
                    c.live = m;
                    return false;
                }
            }
        }
        if (!quiet(player)) return false;
        skipped++;
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: uniqueaccessories_rose_cleanup_candidates applies (the Rose of Temptation cleanup scan runs only while a lured mob is in the level)");
        }
        return true;
    }
}
