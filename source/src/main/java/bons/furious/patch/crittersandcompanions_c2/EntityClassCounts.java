package bons.furious.patch.crittersandcompanions_c2;

import java.lang.reflect.Method;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.entity.PartEntity;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch vanilla_entity_class_count_layer (Minecraft 1.21.1 + NeoForge 21.1.252, both sides): per-level
 * counts of a few entity classes, kept in the structure Level.getEntitiesOfClass searches. No Mojang code here.
 *
 * A level's entities live in its EntitySectionStorage (one per ServerLevel / ClientLevel): every entity is in exactly one
 * EntitySection (a ClassInstanceMultiMap), and every entity search (getEntitiesOfClass -> Level.getEntities ->
 * LevelEntityGetterAdapter -> EntitySectionStorage.getEntities -> EntitySection.getEntities -> ClassInstanceMultiMap.find)
 * walks those sections. Entities enter and leave a section only through EntitySection.add and EntitySection.remove (jar
 * census of every client jar, Jar-in-Jar included, 171,179 classes on 1.20.1; on 1.21.1 the same holds for NeoForge
 * 21.1.252 and every pinned target jar: callers are only the two vanilla section managers and their callbacks; sections
 * are only created by EntitySectionStorage.createSection; nothing else writes a section's multimap), so counting there
 * sees every instance in every section, hidden ones included (a superset of what a search can find; it never under-counts).
 *
 * Tracked classes (subclasses included), by exact name, so counting starts before their mod's classes load:
 *   RED_PANDA        Critters and Companions 2.7.0 RedPandaEntity     (crittersandcompanions_red_panda_gate)
 *   BOSS_SPAWNER     FD Bosses 3.1.0.3 BossSpawnerEntity             (fdbosses_spawner_presence_gate)
 *   KINETIC_FIELD    FD Bosses 3.1.0.3 ChesedKineticFieldEntity      (fdbosses_spawner_presence_gate)
 *   MALKUTH_SPAWNER  FD Bosses 3.1.0.3 MalkuthBossSpawner            (fdbosses_spawner_presence_gate)
 * A gate asks {@link #none(Level, int)}; true only when the level's storage holds no instance of that class, the asking
 * thread owns the storage, and nothing ever made the counts doubtful. Then getEntitiesOfClass(that class, any box, any
 * predicate) returns an empty list (NeoForge's part-entity pass cannot add one: a tracked class is never a PartEntity), and
 * its only other effect is the profiler counter "getEntities", which the gates keep.
 *
 * Exactness of the counts. Per storage, count[c] = the number of entries of class c (or a subclass) in the "all instances"
 * lists of its sections. An add counts the entity when the multimap took it; a removal of a tracked entity (or any removal
 * in a section holding one) first finds the element ArrayList.remove will take out (the first one equal to the argument;
 * Entity.equals compares ids) and, after the removal, uncounts it only if that element is the argument itself. Anything
 * else (another entity with the same id removed instead, an add or removal on a thread other than the one that created the
 * storage, a section that no storage created) marks the storage (or every storage) as doubtful for good: its gates then
 * always run the original search. -Dbons_and_furious.entityClassCountLayer=false turns the gates' use of the layer off.
 * SHADOW MODE for rigs: -Dbons_and_furious.entityClassCountLayer.shadow=true recounts the asked class by walking every
 * section whenever a gate would be told "none", counts SHADOW_CHECKS / SHADOW_MISMATCHES (WARN for the first 20) and
 * answers "unknown", so every gate runs its original search.
 *
 * Ported to 1.21.1: EntitySection, EntitySectionStorage, LevelEntityGetterAdapter, ClassInstanceMultiMap, both section
 * managers, Level.getEntities and EntityGetter.getEntitiesOfClass have the same bodies (Forge's part-entity pass and
 * section hooks are now NeoForge's); Critters and Companions 2.7.0 moved RedPandaEntity to
 * io.github.bonsaistudi0s.crittersandcompanions.common.entity (was com.github.eterdelta.crittersandcompanions.entity);
 * the FD Bosses names are unchanged; the vanilla-search check lists the Mojang method names (the 1.20.1 SRG list collapsed
 * to three distinct names: getEntitiesOfClass, getEntities, getPartEntities; matching every overload by name only makes
 * the check stricter, never looser).
 */
public final class EntityClassCounts {
    /** Runtime switch. -Dbons_and_furious.entityClassCountLayer=false: none() always answers false (gates run the original). */
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.entityClassCountLayer", "true"));
    /** Shadow mode (see the class comment). */
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.entityClassCountLayer.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();

    public static final int RED_PANDA = 0, BOSS_SPAWNER = 1, KINETIC_FIELD = 2, MALKUTH_SPAWNER = 3;
    static final String[] TRACKED = {
            "io.github.bonsaistudi0s.crittersandcompanions.common.entity.RedPandaEntity",
            "com.finderfeed.fdbosses.content.entities.base.BossSpawnerEntity",
            "com.finderfeed.fdbosses.content.entities.chesed_boss.kinetic_field.ChesedKineticFieldEntity",
            "com.finderfeed.fdbosses.content.entities.malkuth_boss.malkuth_boss_spawner.MalkuthBossSpawner"};
    public static final int TRACKED_COUNT = TRACKED.length;
    /** Marker for "no element of the section equals the entity being removed" (beforeRemove's null means "nothing to do"). */
    public static final Object ABSENT = new Object();

    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    private static volatile boolean announced;
    private static volatile boolean unattributed;   // a tracked entity entered a section no storage created: all doubtful
    private static int warnings;

    /** Bit i set when TRACKED[i] is the class or one of its superclasses. */
    private static final ClassValue<Integer> MASK = new ClassValue<>() {
        @Override
        protected Integer computeValue(Class<?> type) {
            int m = 0;
            for (Class<?> c = type; c != null; c = c.getSuperclass()) {
                String n = c.getName();
                for (int i = 0; i < TRACKED.length; i++) if (TRACKED[i].equals(n)) m |= 1 << i;
            }
            return m;
        }
    };

    /**
     * The level classes whose entity search is vanilla's (Level.getEntities + NeoForge's part-entity pass over
     * getPartEntities): ServerLevel and ClientLevel themselves, or a subclass that declares none of the search methods
     * (getEntitiesOfClass, any getEntities overload including the LevelEntityGetter getter, getPartEntities). NeoForge
     * 1.21.1 runs Mojang names in production, so the declared-method names are these.
     */
    private static final Set<String> SEARCH_METHODS = Set.of("getEntitiesOfClass", "getEntities", "getPartEntities");
    private static final ClassValue<Boolean> VANILLA_SEARCH = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            try {
                for (Class<?> c = type; c != null && !c.getName().equals("net.minecraft.world.level.Level"); c = c.getSuperclass()) {
                    String n = c.getName();
                    if (n.equals("net.minecraft.server.level.ServerLevel") || n.equals("net.minecraft.client.multiplayer.ClientLevel")) return true;
                    for (Method m : c.getDeclaredMethods()) if (SEARCH_METHODS.contains(m.getName())) return false;
                }
            } catch (Throwable t) {
                return false;   // a level class whose methods cannot be listed searches "unknown"
            }
            return false;
        }
    };

    private EntityClassCounts() {
    }

    // ------------------------------------------------------------------------------------------ duck interfaces

    /** EntitySectionStorage (EntityClassCountStorageMixin): the counts, the thread that created it, the doubt flag. */
    public interface Storage {
        int[] bons$classCounts();

        Thread bons$classCountOwner();

        boolean bons$classCountsDoubtful();

        void bons$classCountsDoubtful(boolean doubtful);

        /** Shadow mode: the number of entries of tracked class {@code index} in every section, counted by walking them. */
        int bons$classCountTruth(int index);
    }

    /** EntitySection (EntityClassCountSectionMixin): its storage, its tracked members, the removal in progress. */
    public interface Section {
        Storage bons$classCountStorage();

        void bons$classCountStorage(Storage storage);

        int bons$trackedMembers();

        void bons$trackedMembers(int members);

        /** Shadow mode: entries of tracked class {@code index} in this section's "all instances" list. */
        int bons$trackedTruth(int index);
    }

    /** Level (LevelClassCountMixin): the storage its entity searches walk, or null. */
    public interface CountedLevel {
        Storage bons$classCountStorage();
    }

    /** LevelEntityGetterAdapter (EntityGetterClassCountMixin): its section storage, or null. */
    public interface CountedGetter {
        Storage bons$classCountStorage();
    }

    // ------------------------------------------------------------------------------------------ counting (mixin hooks)

    public static int mask(Object entity) {
        return MASK.get(entity.getClass());
    }

    /** Index of a tracked class by its exact name, or -1 (never for a PartEntity type: the gates rely on that). */
    public static int index(Class<?> c) {
        return c == null ? -1 : INDEX.get(c);
    }

    private static final ClassValue<Integer> INDEX = new ClassValue<>() {
        @Override
        protected Integer computeValue(Class<?> c) {
            if (PartEntity.class.isAssignableFrom(c)) return -1;
            String n = c.getName();
            for (int i = 0; i < TRACKED.length; i++) if (TRACKED[i].equals(n)) return i;
            return -1;
        }
    };

    /**
     * none(level, index(searched)) for a search of class {@code searched} in {@code level}: true only when such a search is
     * certain to find nothing. The entry point of fdbosses_spawner_presence_gate (group fdbosses_c2), which reaches it
     * through a method handle so that its group compiles on its own. Keep this class and method name: fdbosses_c2's
     * SpawnerPresence looks them up by name.
     */
    public static boolean noneOf(Level level, Class<?> searched) {
        int i = index(searched);
        return i >= 0 && none(level, i);
    }

    /** First construction of a storage with the layer applied. */
    public static void storageCreated() {
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: vanilla_entity_class_count_layer counts {} entity classes per level for crittersandcompanions_red_panda_gate "
                    + "and fdbosses_spawner_presence_gate{}", TRACKED.length, SHADOW ? " (SHADOW MODE: the gates always run their original searches)" : "");
        }
    }

    /** EntitySection.add, after the multimap took the entity. */
    public static void added(Section section, Object entity) {
        int m = MASK.get(entity.getClass());
        if (m == 0) return;
        Storage s = section.bons$classCountStorage();
        if (s == null) {
            doubtAll("a tracked entity entered a section that no entity storage created");
            return;
        }
        if (Thread.currentThread() != s.bons$classCountOwner()) {
            doubt(s, "a tracked entity was added on thread " + Thread.currentThread().getName() + ", not the thread that owns the level's entities");
            return;
        }
        int[] c = s.bons$classCounts();
        for (int i = 0; i < c.length; i++) if ((m & (1 << i)) != 0) c[i]++;
        section.bons$trackedMembers(section.bons$trackedMembers() + 1);
    }

    /**
     * EntitySection.remove, before the multimap removes: null when neither the entity nor any member of the section is
     * tracked (the removal cannot change a count); otherwise the element ArrayList.remove will take out of the "all
     * instances" list (the first one the entity equals), or ABSENT.
     */
    public static Object beforeRemove(Section section, Iterable<?> members, Object entity) {
        if (section.bons$trackedMembers() == 0 && MASK.get(entity.getClass()) == 0) return null;
        Storage s = section.bons$classCountStorage();
        if (s == null) {
            doubtAll("a tracked entity left a section that no entity storage created");
            return null;
        }
        if (Thread.currentThread() != s.bons$classCountOwner()) {
            doubt(s, "a tracked entity was removed on thread " + Thread.currentThread().getName() + ", not the thread that owns the level's entities");
            return null;
        }
        for (Object x : members) if (entity.equals(x)) return x;
        return ABSENT;
    }

    /** EntitySection.remove, after the multimap removed (or not); {@code first} is what beforeRemove returned (not null). */
    public static void afterRemove(Section section, Object entity, Object first, boolean removed) {
        Storage s = section.bons$classCountStorage();
        if (s == null || s.bons$classCountsDoubtful()) return;
        if (first == ABSENT) {
            if (removed) doubt(s, "a section removed an entity although none was equal to it");
            return;
        }
        if (!removed) {
            doubt(s, "a section kept an entity equal to the one removed");
            return;
        }
        if (first != entity) {
            doubt(s, "removing " + entity + " took out another entity with the same id (" + first + ")");
            return;
        }
        int m = MASK.get(entity.getClass());
        if (m == 0) return;
        int[] c = s.bons$classCounts();
        for (int i = 0; i < c.length; i++) if ((m & (1 << i)) != 0) c[i]--;
        int left = section.bons$trackedMembers() - 1;
        section.bons$trackedMembers(left);
        if (left < 0) doubt(s, "a section's tracked-member count went below zero");
    }

    private static void doubt(Storage s, String why) {
        if (s.bons$classCountsDoubtful()) return;
        s.bons$classCountsDoubtful(true);
        warn("Bons and Furious: vanilla_entity_class_count_layer: " + why + "; the gates of that level run their original searches from now on");
    }

    private static void doubtAll(String why) {
        if (unattributed) return;
        unattributed = true;
        warn("Bons and Furious: vanilla_entity_class_count_layer: " + why + "; every level's gates run their original searches from now on");
    }

    private static synchronized void warn(String text) {
        if (warnings++ < 10) LOGGER.warn(text);
    }

    // ------------------------------------------------------------------------------------------ the gates' question

    /**
     * True only when {@code level}'s entity searches certainly find no instance of tracked class {@code index}: the layer is
     * applied and enabled, the level searches the vanilla way, the calling thread owns the level's entity storage, the counts
     * were never doubtful and the count is 0. False means "unknown": the caller runs its original search.
     */
    public static boolean none(Level level, int index) {
        if (!enabled || unattributed || !(level instanceof CountedLevel counted)) return false;
        Storage s = counted.bons$classCountStorage();
        if (s == null || s.bons$classCountOwner() != Thread.currentThread() || s.bons$classCountsDoubtful()) return false;
        if (s.bons$classCounts()[index] != 0 || !VANILLA_SEARCH.get(level.getClass())) return false;
        if (SHADOW) {
            SHADOW_CHECKS.incrementAndGet();
            int truth = s.bons$classCountTruth(index);
            if (truth != 0 && SHADOW_MISMATCHES.incrementAndGet() <= 20) {
                LOGGER.warn("Bons and Furious: vanilla_entity_class_count_layer SHADOW MISMATCH {}: count 0 for {} in {} but {} found by walking every section",
                        SHADOW_MISMATCHES.get(), TRACKED[index], level, truth);
            }
            return false;
        }
        return true;
    }
}
