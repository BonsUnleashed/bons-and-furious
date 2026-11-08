package bons.furious.patch.goetyhostility_coo49;

import com.Polarice3.Goety.api.entities.IOwned;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious 1.0.49, group goetyhostility_coo49 (Goety Hostility 1.4.4 for 1.21.1 on Goety 3.2.2; NeoForge 1.21.1;
 * server). Helper of the group's mixins; Goety Hostility is all rights reserved: no code of it is carried, the logic is
 * ours.
 *
 * <p>goetyhostility_squall_golem_scan_skip: HostileSquallGolem.tick (tick), in proximity mode, asks the level for every
 * LivingEntity in its box grown by 6 on every server tick, keeps the last one that MobUtil.isOwnedTargetable(this, it) and
 * it.hasLineOfSight(this) accept, and then sets activeTime = 100 when it found one OR the golem has a target. While
 * getTarget() is not null the found entity cannot change anything, and the loop only reads: for this golem (an Enemy that
 * is not one of Goety's IOwned, checked per class) isOwnedTargetable is "the candidate is a Player that is not creative or
 * spectator" (pure), and hasLineOfSight, asked only for a player that test accepted, is vanilla's clip test (pure); the
 * search's predicate is NO_SPECTATORS. So the search is marked UNUSED for vanilla_entity_walk_skip: the call
 * itself runs (profiler count, Valkyrien Skies' box hooks, NeoForge's part entity pass), only its walk over the entity
 * sections is left out; the loop then sees what that pass adds (no LivingEntity part in practice) and activeTime becomes
 * 100 as before.
 *
 * <p>goetyhostility_wildfire_drop_probe_lean: HostileWildfire.steepDropBelow reads the eight block states straight below
 * the boss (blockPosition() minus 0..7) and answers "no non-air block among them". The first non-air block settles it, so
 * the lean probe stops there and reuses one mutable position: the same answer, the same first read (all eight positions
 * lie in one chunk column; below the world's bottom getBlockState answers VOID_AIR without chunk access), up to seven fewer
 * block reads and eight fewer BlockPos per call.
 *
 * <p>Runtime flags (=false: off): bons_and_furious.goetyhostilitySquallGolemScanSkip,
 * bons_and_furious.goetyhostilityWildfireDropProbeLean. No shadow mode: the squall skip is a proven read-only path whose
 * result is discarded; the probe is a plain loop.
 */
public final class GhScans {
    static final String SQUALL_KEY = "goetyhostility_squall_golem_scan_skip", PROBE_KEY = "goetyhostility_wildfire_drop_probe_lean";
    public static volatile boolean squall = flag("goetyhostilitySquallGolemScanSkip");
    public static volatile boolean probe = flag("goetyhostilityWildfireDropProbeLean");
    /** Statistics (plain fields): searches marked unused, probes answered by the lean loop. */
    public static long marked, probes;

    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    private static volatile boolean announcedSquall, announcedProbe;

    /** The last class PURE_LOOP answered true for (plain field: a race only repeats the ClassValue lookup). */
    private static Class<?> pureClass;

    private static boolean pure(Class<?> c) {
        if (c == pureClass) return true;
        if (!PURE_LOOP.get(c)) return false;
        pureClass = c;
        return true;
    }

    /**
     * The golem classes for which the loop is the pure "player in sight" test (an Enemy that is not one of Goety's IOwned)
     * and getTarget() is Mob's own field read (so asking it first changes nothing).
     */
    private static final ClassValue<Boolean> PURE_LOOP = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> c) {
            try {
                return Enemy.class.isAssignableFrom(c) && !IOwned.class.isAssignableFrom(c) && c.getMethod("getTarget").getDeclaringClass() == Mob.class;
            } catch (Throwable t) {
                return false;
            }
        }
    };

    private GhScans() {
    }

    private static boolean flag(String name) {
        return !"false".equalsIgnoreCase(System.getProperty("bons_and_furious." + name, "true"));
    }

    /** The Level.getEntitiesOfClass(LivingEntity, box) call of HostileSquallGolem.tick. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static List squallScan(Object golem, Level level, Class<?> searched, Object box, Operation<List> original) {
        // the class check through a one-entry cache before the ClassValue (a ClassValue lookup on
        // every call put the stand-aside path, a golem without a target, at 0.978 of the shipped call); getTarget is only
        // read for a class whose getTarget is Mob's own
        if (!squall || !(golem instanceof Mob mob) || !pure(golem.getClass()) || mob.getTarget() == null
                || !EntityPresenceLink.markUnused(SQUALL_KEY, level, searched)) {
            return original.call(level, searched, box);
        }
        marked++;
        if (!announcedSquall) {
            announcedSquall = true;
            LOGGER.info("Bons and Furious: {}: a Squall Golem with a target no longer walks the entities around it for a wake-up check whose answer it ignores", SQUALL_KEY);
        }
        try {
            return original.call(level, searched, box);
        } finally {
            EntityPresenceLink.unmark();
        }
    }

    /** HostileWildfire.steepDropBelow: true when the eight block states below the boss are all air. */
    public static boolean steepDropBelow(Entity boss) {
        if (!announcedProbe) {
            announcedProbe = true;
            LOGGER.info("Bons and Furious: {}: the Wildfire's drop probe stops at the first solid block", PROBE_KEY);
        }
        probes++;
        Level level = boss.level();
        BlockPos at = boss.blockPosition();
        int x = at.getX(), y = at.getY(), z = at.getZ();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int i = 0; i < 8; i++) {
            if (!level.getBlockState(pos.set(x, y - i, z)).isAir()) return false;
        }
        return true;
    }
}
