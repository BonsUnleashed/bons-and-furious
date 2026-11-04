package bons.furious.patch.crittersandcompanions_c2;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.world.level.Level;
import net.minecraftforge.entity.PartEntity;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch vanilla_entity_class_count_layer (Minecraft 1.20.1 + Forge 47.4.16, both sides): per-level counts
 * of a few entity classes, kept in the structure Level.getEntitiesOfClass searches. No Mojang code here.
 *
 * A level's entities live in its EntitySectionStorage (one per ServerLevel / ClientLevel): every entity is in exactly one
 * EntitySection (a ClassInstanceMultiMap), and every entity search (getEntitiesOfClass -> Level.getEntities ->
 * LevelEntityGetterAdapter -> EntitySectionStorage.getEntities -> EntitySection.getEntities -> ClassInstanceMultiMap.find)
 * walks those sections. Entities enter and leave a section only through EntitySection.add and EntitySection.remove (jar
 * census of every client jar, Jar-in-Jar included, 171,179 classes: callers are only the two vanilla section managers and
 * their callbacks; sections are only created by EntitySectionStorage.createSection; nothing else writes a section's
 * multimap), so counting there sees every instance in every section, hidden ones included (a superset of what a search
 * can find; it never under-counts).
 *
 * Tracked classes (subclasses included), by exact name, so counting starts before their mod's classes load:
 *   RED_PANDA        Critters and Companions 2.3.5 RedPandaEntity     (crittersandcompanions_red_panda_gate)
 *   BOSS_SPAWNER     FD Bosses 3.1.0.3 BossSpawnerEntity             (fdbosses_spawner_presence_gate)
 *   KINETIC_FIELD    FD Bosses 3.1.0.3 ChesedKineticFieldEntity      (fdbosses_spawner_presence_gate)
 *   MALKUTH_SPAWNER  FD Bosses 3.1.0.3 MalkuthBossSpawner            (fdbosses_spawner_presence_gate)
 * Since 1.0.45, for the camera-shake presence gates (group client_shake_gate):
 *   SCREEN_SHAKE     Cataclysm 3.16 ScreenShake_Entity                (cataclysm_shake_scan_presence_gate)
 *   LM_CAMERA_SHAKE  Legendary Monsters 2.2.2 CameraShakeEntity       (legendary_monsters_shake_scan_presence_gate)
 *   LM_CAMERA_ZOOM   Legendary Monsters 2.2.2 DynamicCameraZoomEntity (legendary_monsters_shake_scan_presence_gate)
 *   MOWZIE_SHAKE     Mowzie's Mobs 1.7.3 EntityCameraShake            (mowziesmobs_shake_scan_presence_gate)
 *   CF_SHAKE         Cryptic Foes 1.0.4 EntityCameraShake             (crypticfoes_shake_scan_presence_gate)
 *   SHAKES_SCREEN    Alex's Caves 2.0.2 ShakesScreen, an INTERFACE    (alexscaves_shake_scan_presence_gate)
 * Since 1.0.46, for the projectile hit-test gates (group projectile_gates; they find these through trackedIndex):
 *   IA_VEHICLE       Immersive Aircraft 1.1.8 VehicleEntity          (immersiveaircraft_vehicle_trace_gate; also Immersive
 *                    Machinery's and Man of Many Planes' vehicles, which extend it)
 *   BONE_HITBOX      KnightLib 2.0.3 BoneHitboxHolder, an INTERFACE  (knightlib_bone_hitbox_gate)
 * Since 1.0.49: any number of further entries, which other groups REGISTER from their own resources
 * (bons_and_furious.&lt;group&gt;.entity_classes.json, see EntityClassRegistrations), numbered after the twelve above; a gate
 * finds its entry with {@link #trackedIndex(String)}. The twelve built-in entries keep their indices and their behaviour;
 * the per-class index list replaced the 32-bit mask, so the number of entries has no limit.
 * An interface entry counts every entity whose class (or a superclass) implements it, directly or through another
 * interface (the runtime class's interfaces, so an interface a mixin adds counts too).
 * A gate asks {@link #none(Level, int)}; true only when the level's storage holds no instance of that class, the asking
 * thread owns the storage, and nothing ever made the counts doubtful. Then getEntitiesOfClass(that class, any box, any
 * predicate) returns an empty list (Forge's part-entity pass cannot add one: a tracked class is never a PartEntity), and its
 * only other effect is the profiler counter "getEntities", which the gates keep. For an interface entry, none() says that
 * no entity in the level's entity sections implements it; part entities are not in sections, so a gate that uses an
 * interface entry must show by itself that no part entity can reach its search (the Alex's Caves gate searches Mob, and a
 * PartEntity is never a Mob). Since 1.0.49 {@link #count(Level, int)} answers the number itself under the same conditions.
 *
 * Exactness of the counts. Per storage, count[c] = the number of entries of class c (or a subclass) in the "all instances"
 * lists of its sections. An add counts the entity when the multimap took it; a removal of a tracked entity (or any removal
 * in a section holding one) first finds the element ArrayList.remove will take out (the first one equal to the argument;
 * Entity.equals compares ids) and, after the removal, uncounts it only if that element is the argument itself. Anything
 * else (another entity with the same id removed instead, an add or removal on a thread other than the one that created the
 * storage, a section that no storage created) marks the storage (or every storage) as doubtful for good: its gates then
 * always run the original search. -Dbons_and_furious.entityClassCountLayer=false turns the gates' use of the layer off.
 * SHADOW MODE for rigs: -Dbons_and_furious.entityClassCountLayer.shadow=true recounts the asked class by walking every
 * section whenever a gate would be told "none" (or a count), counts SHADOW_CHECKS / SHADOW_MISMATCHES (WARN for the first
 * 20) and answers "unknown", so every gate runs its original search.
 */
public final class EntityClassCounts {
    /** Runtime switch. -Dbons_and_furious.entityClassCountLayer=false: none() always answers false (gates run the original). */
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.entityClassCountLayer", "true"));
    /** Shadow mode (see the class comment). */
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.entityClassCountLayer.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();

    public static final int RED_PANDA = 0, BOSS_SPAWNER = 1, KINETIC_FIELD = 2, MALKUTH_SPAWNER = 3;
    /** Since 1.0.45: the camera-shake presence gates (group client_shake_gate; they find these through trackedIndex). */
    public static final int SCREEN_SHAKE = 4, LM_CAMERA_SHAKE = 5, LM_CAMERA_ZOOM = 6, MOWZIE_SHAKE = 7, CF_SHAKE = 8, SHAKES_SCREEN = 9;
    /** Since 1.0.46: the projectile hit-test gates (group projectile_gates; they find these through trackedIndex). */
    public static final int IA_VEHICLE = 10, BONE_HITBOX = 11;
    /** The entries of 1.0.48, in their index order (unchanged). */
    private static final String[] BUILT_IN = {
            "com.github.eterdelta.crittersandcompanions.entity.RedPandaEntity",
            "com.finderfeed.fdbosses.content.entities.base.BossSpawnerEntity",
            "com.finderfeed.fdbosses.content.entities.chesed_boss.kinetic_field.ChesedKineticFieldEntity",
            "com.finderfeed.fdbosses.content.entities.malkuth_boss.malkuth_boss_spawner.MalkuthBossSpawner",
            "com.github.L_Ender.cataclysm.entity.effect.ScreenShake_Entity",
            "net.miauczel.legendary_monsters.entity.AnimatedMonster.Effect.CameraShakeEntity",
            "net.miauczel.legendary_monsters.effect.DynamicCameraZoomEntity",
            "com.bobmowzie.mowziesmobs.server.entity.effects.EntityCameraShake",
            "com.min01.crypticfoes.entity.EntityCameraShake",
            "com.github.alexmodguy.alexscaves.server.entity.util.ShakesScreen",
            "immersive_aircraft.entity.VehicleEntity",
            "dev.xylonity.knightlib.api.entity.hitbox.BoneHitboxHolder"};
    /** Number of built-in entries (the registered ones follow them). */
    public static final int BUILT_IN_COUNT = BUILT_IN.length;
    /** Every tracked name in index order: BUILT_IN, then the registered ones (since 1.0.49). */
    static final String[] TRACKED;
    /** The group that registered each entry ("" = built in), for the start-up line and probes. */
    private static final String[] REGISTERED_BY;
    public static final int TRACKED_COUNT;
    private static final Map<String, Integer> BY_NAME = new HashMap<>();
    private static final EntityClassRegistrations.Table TABLE;

    static {
        TABLE = EntityClassRegistrations.load(BUILT_IN);
        TRACKED = TABLE.names();
        REGISTERED_BY = TABLE.groups();
        TRACKED_COUNT = TRACKED.length;
        for (int i = 0; i < TRACKED.length; i++) BY_NAME.putIfAbsent(TRACKED[i], i);
    }

    /** Marker for "no element of the section equals the entity being removed" (beforeRemove's null means "nothing to do"). */
    public static final Object ABSENT = new Object();
    private static final int[] NOTHING = new int[0];

    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    private static volatile boolean announced;
    private static volatile boolean unattributed;   // a tracked entity entered a section no storage created: all doubtful
    private static int warnings;

    /**
     * The tracked indices (ascending) whose entry is the class, one of its superclasses, or (since 1.0.45) an interface any
     * of them implements, directly or through another interface. Since 1.0.49 an index list instead of a 32-bit mask (any
     * number of entries); the shared empty array for every untracked class.
     */
    private static final ClassValue<int[]> MASKS = new ClassValue<>() {
        @Override
        protected int[] computeValue(Class<?> type) {
            java.util.BitSet bits = new java.util.BitSet();
            for (Class<?> c = type; c != null; c = c.getSuperclass()) {
                bit(bits, c.getName());
                interfaceBits(bits, c);
            }
            return bits.isEmpty() ? NOTHING : bits.stream().toArray();
        }
    };

    private static void bit(java.util.BitSet bits, String name) {
        Integer i = BY_NAME.get(name);
        if (i != null) bits.set(i);
    }

    /** The entries of every interface {@code c} declares, and of the interfaces those extend (each visited once). */
    private static void interfaceBits(java.util.BitSet bits, Class<?> c) {
        java.util.ArrayDeque<Class<?>> todo = new java.util.ArrayDeque<>(Arrays.asList(c.getInterfaces()));
        Set<Class<?>> seen = new java.util.HashSet<>();
        while (!todo.isEmpty()) {
            Class<?> i = todo.pop();
            if (!seen.add(i)) continue;
            bit(bits, i.getName());
            todo.addAll(Arrays.asList(i.getInterfaces()));
        }
    }

    /**
     * The level classes whose entity search is vanilla's (Level.getEntities + Forge's part-entity pass over
     * getPartEntities): ServerLevel and ClientLevel themselves, or a subclass that overrides none of the search methods.
     */
    private static final Set<String> SEARCH_METHODS = Set.of("m_45976_", "m_6443_", "m_142425_", "m_261153_", "m_260826_", "getPartEntities", "m_142646_");
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

    /** Since 1.0.49: whether the entity counts for tracked entry {@code index} (its class, a superclass or an interface). */
    public static boolean tracks(Object entity, int index) {
        for (int i : MASKS.get(entity.getClass())) if (i == index) return true;
        return false;
    }

    /** Since 1.0.49: whether the entity counts for any tracked entry. */
    public static boolean tracksAny(Object entity) {
        return MASKS.get(entity.getClass()).length != 0;
    }

    /** Since 1.0.49: the tracked indices the entity counts for (ascending; shared, never modify it). */
    public static int[] trackedIndices(Class<?> type) {
        return MASKS.get(type);
    }

    /**
     * Index of a tracked class by its exact name, or -1 (never for a PartEntity type: the gates rely on that; since 1.0.45
     * never for an interface either, so noneOf only ever speaks for a class an entity search can be made for).
     */
    public static int index(Class<?> c) {
        return c == null ? -1 : INDEX.get(c);
    }

    private static final ClassValue<Integer> INDEX = new ClassValue<>() {
        @Override
        protected Integer computeValue(Class<?> c) {
            if (c.isInterface() || PartEntity.class.isAssignableFrom(c)) return -1;
            Integer i = BY_NAME.get(c.getName());
            return i == null ? -1 : i;
        }
    };

    /**
     * Since 1.0.45: the index of a tracked class or interface by its exact name, or -1. For a gate that must ask about a
     * type it does not search for directly (alexscaves_shake_scan_presence_gate searches Mob for ShakesScreen mobs); such a
     * gate asks {@link #none(Level, int)} and answers for part entities itself (see the class comment). Reached through a
     * method handle, so that group compiles on its own. Since 1.0.49 also the way every registering group finds its entry
     * (-1: not registered in this JVM, e.g. its key is off, its mod absent or its file unreadable: the gate stays off).
     */
    public static int trackedIndex(String name) {
        Integer i = name == null ? null : BY_NAME.get(name);
        return i == null ? -1 : i;
    }

    /** Since 1.0.49: the exact name of tracked entry {@code index} (for log lines), or null. */
    public static String trackedName(int index) {
        return index >= 0 && index < TRACKED_COUNT ? TRACKED[index] : null;
    }

    /**
     * none(level, index(searched)) for a search of class {@code searched} in {@code level}: true only when such a search is
     * certain to find nothing. The entry point of fdbosses_spawner_presence_gate (group fdbosses_c2), which reaches it
     * through a method handle so that its group compiles on its own.
     */
    public static boolean noneOf(Level level, Class<?> searched) {
        int i = index(searched);
        return i >= 0 && none(level, i);
    }

    /** First construction of a storage with the layer applied. */
    public static void storageCreated() {
        if (!announced) {
            announced = true;
            StringBuilder reg = new StringBuilder();
            Map<String, Integer> perGroup = new java.util.TreeMap<>();
            for (int i = BUILT_IN_COUNT; i < TRACKED_COUNT; i++) perGroup.merge(REGISTERED_BY[i], 1, Integer::sum);
            perGroup.forEach((g, n) -> reg.append(reg.length() == 0 ? "" : ", ").append(g).append(' ').append(n));
            LOGGER.info("Bons and Furious: vanilla_entity_class_count_layer counts {} entity classes per level ({} built in, {} registered from {}{}){}",
                    TRACKED_COUNT, BUILT_IN_COUNT, TRACKED_COUNT - BUILT_IN_COUNT, TABLE.source(), reg.length() == 0 ? "" : ": " + reg,
                    SHADOW ? " (SHADOW MODE: the gates always run their original searches)" : "");
        }
    }

    /** EntitySection.add, after the multimap took the entity. */
    public static void added(Section section, Object entity) {
        int[] m = MASKS.get(entity.getClass());
        if (m.length == 0) return;
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
        for (int i : m) c[i]++;
        section.bons$trackedMembers(section.bons$trackedMembers() + 1);
    }

    /**
     * EntitySection.remove, before the multimap removes: null when neither the entity nor any member of the section is
     * tracked (the removal cannot change a count); otherwise the element ArrayList.remove will take out of the "all
     * instances" list (the first one the entity equals), or ABSENT.
     */
    public static Object beforeRemove(Section section, Iterable<?> members, Object entity) {
        if (section.bons$trackedMembers() == 0 && MASKS.get(entity.getClass()).length == 0) return null;
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
        int[] m = MASKS.get(entity.getClass());
        if (m.length == 0) return;
        int[] c = s.bons$classCounts();
        for (int i : m) c[i]--;
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

    /** The level's counted storage when its counts may be used by this thread now, else null. */
    private static Storage usable(Level level, int index) {
        if (!enabled || unattributed || index < 0 || index >= TRACKED_COUNT || !(level instanceof CountedLevel counted)) return null;
        Storage s = counted.bons$classCountStorage();
        if (s == null || s.bons$classCountOwner() != Thread.currentThread() || s.bons$classCountsDoubtful()) return null;
        return VANILLA_SEARCH.get(level.getClass()) ? s : null;
    }

    /**
     * True only when {@code level}'s entity searches certainly find no instance of tracked class {@code index}: the layer is
     * applied and enabled, the level searches the vanilla way, the calling thread owns the level's entity storage, the counts
     * were never doubtful and the count is 0. False means "unknown": the caller runs its original search.
     */
    public static boolean none(Level level, int index) {
        if (!enabled || unattributed || !(level instanceof CountedLevel counted)) return false;
        if (index < 0 || index >= TRACKED_COUNT) return false;
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

    /**
     * Since 1.0.49: the number of entities of tracked class {@code index} in {@code level}'s entity sections (hidden ones
     * included), under the same conditions as {@link #none}; -1 means "unknown" (the caller runs its original code). In
     * shadow mode the number is recounted by walking every section, compared, and -1 is answered.
     */
    public static int count(Level level, int index) {
        Storage s = usable(level, index);
        if (s == null) return -1;
        int n = s.bons$classCounts()[index];
        if (SHADOW) {
            SHADOW_CHECKS.incrementAndGet();
            int truth = s.bons$classCountTruth(index);
            if (truth != n && SHADOW_MISMATCHES.incrementAndGet() <= 20) {
                LOGGER.warn("Bons and Furious: vanilla_entity_class_count_layer SHADOW MISMATCH {}: count {} for {} in {} but {} found by walking every section",
                        SHADOW_MISMATCHES.get(), n, TRACKED[index], level, truth);
            }
            return -1;
        }
        return n;
    }
}
