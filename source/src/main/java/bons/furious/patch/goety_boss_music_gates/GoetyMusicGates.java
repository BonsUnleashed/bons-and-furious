package bons.furious.patch.goety_boss_music_gates;

import com.Polarice3.Goety.config.MainConfig;
import com.Polarice3.Goety.utils.MiscCapHelper;
import java.util.Set;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * Bons and Furious 1.0.49 group goety_boss_music_gates (client; Minecraft 1.21.1 + NeoForge 21.1). Helper of the group's
 * mixins; no code of the target mods or of Minecraft is carried.
 *
 *   goety_boss_music_class_gate          Goety 3.2.2 ClientEvents.onEntityTick (EntityTickEvent.Post) runs for every entity
 *                                        every tick on both logical sides: on a client level it reads the boss-music option,
 *                                        asks the entity's Goety misc data twice for its target (MiscCapHelper.getMobTarget),
 *                                        may ask the target's owner and tests an entity-type tag, and acts (novelty and attack
 *                                        sounds, pre-boss and boss music) only for entities of 14 classes: SquallGolem,
 *                                        Leapleaf, GuardianServant, Wight, Apostle, Vizier, HostileRedstoneMonstrosity,
 *                                        EnderKeeper, HostileRedstoneGolem, Endersent, Crone, Heresiarch, SkullLord,
 *                                        WitherNecromancer. For an entity of none of them the handler returns at its start.
 *   goetyhostility_boss_music_class_gate Goety Hostility 1.4.4 GHClientForgeEvents.onEntityTick (EntityTickEvent.Post): the
 *                                        same reads, and acts only for HostileWildfire (its theme); for any other entity the
 *                                        handler returns at its start.
 * On NeoForge 1.21.1 Goety's misc data is a data attachment: MiscCapHelper.getCapability is entity.getData(...), which
 * creates the attachment with its default value the first time it is asked. Goety's client handler of MiscCapUpdatePacket
 * only fills an attachment that exists (getExistingData(...).ifPresent), so that creation is the one write the two checks
 * make, and it decides which later server updates the client keeps for the entity. The gates keep it: for a living entity
 * of a client level, while the boss-music option is on (exactly when the check would have asked getMobTarget), they ask
 * MiscCapHelper.getCapability once before returning; everything else the check did before returning is a read (the option,
 * Level.getEntity, OwnableEntity.getOwner, the tag test).
 * Whether a class is one of them is decided once per class (ClassValue) by name through the class and its superclasses,
 * which is what instanceof tests for these (non-interface) classes. Runtime flags (-D...=false turns one off):
 * bons_and_furious.goetyBossMusicClassGate, .goetyhostilityBossMusicClassGate.
 */
public final class GoetyMusicGates {
    public static volatile boolean goety = flag("goetyBossMusicClassGate");
    public static volatile boolean goetyhostility = flag("goetyhostilityBossMusicClassGate");
    /** Statistics for probes (plain fields; the client thread writes them): handler calls returned at their start. */
    public static long skippedGoety, skippedHostility;

    /** The classes Goety's handler acts on (3.2.2). */
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

    private static boolean bossMusic() {
        return (Boolean) MainConfig.BossMusic.get();
    }

    /** Goety's handler can do nothing for this entity: keep its one write, then return at its start. */
    public static boolean skipGoety(Entity entity) {
        if (!goety || entity == null || GOETY_ACTS.get(entity.getClass())) return false;
        if (entity.level() instanceof ClientLevel && entity instanceof LivingEntity living && bossMusic()) MiscCapHelper.getCapability(living);
        skippedGoety++;
        return true;
    }

    /** Goety Hostility's handler can do nothing for this entity: keep its one write, then return at its start. */
    public static boolean skipHostility(Entity entity) {
        if (!goetyhostility || entity == null || HOSTILITY_ACTS.get(entity.getClass())) return false;
        if (entity.level().isClientSide && entity instanceof LivingEntity living && bossMusic()) MiscCapHelper.getCapability(living);
        skippedHostility++;
        return true;
    }
}
