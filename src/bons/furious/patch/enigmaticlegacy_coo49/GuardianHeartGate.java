package bons.furious.patch.enigmaticlegacy_coo49;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.world.entity.monster.Guardian;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch enigmaticlegacy_guardian_heart_idle_gate (Enigmatic Legacy 2.30.1, server side). Ours; no
 * Enigmatic Legacy code is carried. Idea: Collections Of Optimizations (enigmaticlegacy.guardianHeartScanInterval), idea
 * text only.
 *
 * GuardianHeart.inventoryTick runs for every Heart of the Guardian in a player's inventory on every server tick. It first
 * searches all monsters in a box around the player (Monster.class, the ability range in every direction) and then uses
 * that list twice: (1) only when the bearer is cursed, the heart sits in the hotbar and is not cooling down, to pick the
 * monster the player looks at (the ability); (2) always, in a loop that acts only on non-elder guardians without a target.
 * Most of the time (1) does not run, so the whole monster search serves a loop that skips every monster that is not a
 * guardian.
 *
 * Here the monster search is deferred (GuardianHeartGate.Deferred, returned by the wrapped search call): the ability's
 * loop (first List.iterator call of the method) makes the search exactly as shipped; the guardian loop (third
 * List.iterator call) uses that same list when it exists, and otherwise a search of Guardian.class over the same box. A
 * class search walks the same sections in the same order and keeps each section's entity order, so it yields exactly the
 * guardians of the monster list, in the same order, with the same no-spectator predicate; the loop skips every other
 * monster without effect, so the guardian loop does exactly what it did. While the shared entity class counts say the
 * level holds no guardian at all, that search is marked empty for vanilla_entity_walk_skip (the call itself still runs:
 * profiler count, Forge's part entity pass). Between the original search point and the deferred search the method only
 * reads (the cursed-one, hotbar and cooldown checks), so both searches see the same entities. Flag:
 * -Dbons_and_furious.enigmaticlegacyGuardianHeartIdleGate=false; shadow (also runs the monster search and compares the
 * guardians it holds with the guardian search): -Dbons_and_furious.enigmaticlegacyGuardianHeartIdleGate.shadow=true.
 */
public final class GuardianHeartGate {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    static final String KEY = "enigmaticlegacy_guardian_heart_idle_gate";
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.enigmaticlegacyGuardianHeartIdleGate", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.enigmaticlegacyGuardianHeartIdleGate.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong();
    public static final AtomicLong SHADOW_MISMATCHES = new AtomicLong();
    /** Guardian-only searches made instead of the monster search, and monster searches made as shipped. */
    public static final AtomicLong GUARDIAN_SEARCHES = new AtomicLong();
    public static final AtomicLong MONSTER_SEARCHES = new AtomicLong();
    private static volatile boolean announced;

    private GuardianHeartGate() {
    }

    /** Index of Guardian in the shared class counts (-1 = not counted here), resolved on first use. */
    private static final class Index {
        static final int GUARDIAN = EntityPresenceLink.trackedIndex("net.minecraft.world.entity.monster.Guardian");
    }

    /** The wrapped monster search (Level.getEntitiesOfClass(Monster.class, box)): deferred while the switch is on. */
    public static List<?> monsters(Level level, Class<?> cls, AABB box, Operation<List<?>> original) {
        if (!enabled) return original.call(level, cls, box);
        return new Deferred(level, cls, box, original);
    }

    /** The ability's loop over the monster list: the search as shipped. */
    public static Iterator<?> abilityLoop(List<?> list, Operation<Iterator<?>> original) {
        return list instanceof Deferred d ? d.full().iterator() : original.call(list);
    }

    /** The guardian loop over the monster list: that list when it was made, else the guardians of the same box. */
    public static Iterator<?> guardianLoop(List<?> list, Operation<Iterator<?>> original) {
        return list instanceof Deferred d ? d.forGuardianLoop().iterator() : original.call(list);
    }

    /** The monster list, made only when something reads it. */
    public static final class Deferred extends AbstractList<Object> {
        private final Level level;
        private final Class<?> cls;
        private final AABB box;
        private final Operation<List<?>> original;
        private List<?> full;

        Deferred(Level level, Class<?> cls, AABB box, Operation<List<?>> original) {
            this.level = level;
            this.cls = cls;
            this.box = box;
            this.original = original;
        }

        /** The search exactly as shipped (once). */
        public List<?> full() {
            List<?> f = this.full;
            if (f == null) {
                this.full = f = this.original.call(this.level, this.cls, this.box);
                MONSTER_SEARCHES.incrementAndGet();
            }
            return f;
        }

        List<?> forGuardianLoop() {
            if (this.full != null) return this.full;
            List<?> g = guardians(this.level, this.box);
            GUARDIAN_SEARCHES.incrementAndGet();
            if (!announced) {
                announced = true;
                LOGGER.info("Bons and Furious: enigmaticlegacy_guardian_heart_idle_gate applies (an idle Heart of the Guardian searches only guardians)");
            }
            if (SHADOW) shadow(g);
            return g;
        }

        private void shadow(List<?> guardians) {
            List<?> monsters = this.original.call(this.level, this.cls, this.box);
            List<Object> expected = new ArrayList<>();
            for (Object m : monsters) if (m instanceof Guardian) expected.add(m);
            SHADOW_CHECKS.incrementAndGet();
            boolean same = expected.size() == guardians.size();
            for (int i = 0; same && i < expected.size(); i++) same = expected.get(i) == guardians.get(i);
            if (!same && SHADOW_MISMATCHES.incrementAndGet() <= 20) {
                LOGGER.warn("Bons and Furious: enigmaticlegacy_guardian_heart_idle_gate shadow mismatch: {} guardians in the monster search, {} in the guardian search", expected.size(), guardians.size());
            }
        }

        @Override
        public Object get(int index) {
            return full().get(index);
        }

        @Override
        public int size() {
            return full().size();
        }
    }

    /** Level.getEntitiesOfClass(Guardian.class, box), marked empty for the walk hook while the level holds no guardian. */
    static List<?> guardians(Level level, AABB box) {
        if (!EntityPresenceLink.markEmpty(KEY, level, Guardian.class, Index.GUARDIAN)) return level.m_45976_(Guardian.class, box);
        try {
            return level.m_45976_(Guardian.class, box);
        } finally {
            EntityPresenceLink.unmark();
        }
    }
}
