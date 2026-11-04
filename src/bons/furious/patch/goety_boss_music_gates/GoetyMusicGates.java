package bons.furious.patch.goety_boss_music_gates;

import java.util.Set;

/**
 * Bons and Furious 1.0.49 group goety_boss_music_gates (client; Minecraft 1.20.1 + Forge 47.4.28). Helper of the group's
 * mixins; no code of the target mods or of Minecraft is carried.
 *
 *   goety_boss_music_class_gate          Goety 2.5.60.0 (also 2.5.58.4) ClientEvents.onEntityTick runs for every living
 *                                        entity every client tick: it reads the boss-music option, resolves the entity's Goety
 *                                        capability twice (its target), may ask the target's owner and tests an entity-type
 *                                        tag, and acts (novelty and attack sounds, pre-boss and boss music) only for entities
 *                                        of 14 classes: SquallGolem, Leapleaf, GuardianServant, Wight, Apostle, Vizier,
 *                                        HostileRedstoneMonstrosity, EnderKeeper, HostileRedstoneGolem, Endersent, Crone,
 *                                        Heresiarch, SkullLord, WitherNecromancer (2.5.58.4 tests the first ten). For an entity
 *                                        of none of them the handler returns at its start: everything it would have done before
 *                                        returning is a read (the option, the capability, Level.getEntity, OwnableEntity.getOwner,
 *                                        the tag test).
 *   goetyhostility_boss_music_class_gate Goety Hostility 1.4.4 GHClientForgeEvents.onEntityTick: the same reads, and acts only for
 *                                        HostileWildfire (its theme); for any other entity the handler returns at its start.
 * Whether a class is one of them is decided once per class (ClassValue) by name through the class and its superclasses,
 * which is what instanceof tests for these (non-interface) classes. Runtime flags (-D...=false turns one off):
 * bons_and_furious.goetyBossMusicClassGate, .goetyhostilityBossMusicClassGate.
 */
public final class GoetyMusicGates {
    public static volatile boolean goety = flag("goetyBossMusicClassGate");
    public static volatile boolean goetyhostility = flag("goetyhostilityBossMusicClassGate");
    /** Statistics for probes (plain fields; the client thread writes them): handler calls returned at their start. */
    public static long skippedGoety, skippedHostility;

    /** The classes Goety's handler acts on (2.5.60.0; 2.5.58.4 acts on the first ten). */
    public static final Set<String> GOETY = Set.of(
            "com.Polarice3.Goety.common.entities.ally.golem.SquallGolem",
            "com.Polarice3.Goety.common.entities.ally.Leapleaf",
            "com.Polarice3.Goety.common.entities.ally.GuardianServant",
            "com.Polarice3.Goety.common.entities.hostile.Wight",
            "com.Polarice3.Goety.common.entities.boss.Apostle",
            "com.Polarice3.Goety.common.entities.boss.Vizier",
            "com.Polarice3.Goety.common.entities.hostile.illagers.HostileRedstoneMonstrosity",
            "com.Polarice3.Goety.common.entities.boss.EnderKeeper",
            "com.Polarice3.Goety.common.entities.hostile.illagers.HostileRedstoneGolem",
            "com.Polarice3.Goety.common.entities.hostile.ender.Endersent",
            "com.Polarice3.Goety.common.entities.hostile.cultists.Crone",
            "com.Polarice3.Goety.common.entities.hostile.cultists.Heresiarch",
            "com.Polarice3.Goety.common.entities.hostile.SkullLord",
            "com.Polarice3.Goety.common.entities.hostile.WitherNecromancer");
    /** The class Goety Hostility's handler acts on. */
    public static final Set<String> HOSTILITY = Set.of("com.ratrod.goetyhostility.common.entities.hostile.HostileWildfire");

    private static final ClassValue<Boolean> GOETY_ACTS = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            return named(type, GOETY);
        }
    };
    private static final ClassValue<Boolean> HOSTILITY_ACTS = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            return named(type, HOSTILITY);
        }
    };

    private GoetyMusicGates() {
    }

    private static boolean flag(String name) {
        return !"false".equalsIgnoreCase(System.getProperty("bons_and_furious." + name, "true"));
    }

    static boolean named(Class<?> type, Set<String> names) {
        for (Class<?> k = type; k != null; k = k.getSuperclass()) if (names.contains(k.getName())) return true;
        return false;
    }

    /** Goety's handler can do nothing for this entity: return at its start. */
    public static boolean skipGoety(Object entity) {
        if (!goety || entity == null || GOETY_ACTS.get(entity.getClass())) return false;
        skippedGoety++;
        return true;
    }

    /** Goety Hostility's handler can do nothing for this entity: return at its start. */
    public static boolean skipHostility(Object entity) {
        if (!goetyhostility || entity == null || HOSTILITY_ACTS.get(entity.getClass())) return false;
        skippedHostility++;
        return true;
    }
}
