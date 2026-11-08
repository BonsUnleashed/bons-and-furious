package bons.furious.patch.sophisticatedbackpacks_coo49;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.LivingEntity;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious 1.0.49 switch sophisticatedbackpacks_living_tick_fast_exit (Sophisticated Backpacks 1.21.1-3.26.20.2244,
 * server; kind opt, on by default; helper of SbLivingTickMixin). No Sophisticated Backpacks code is carried.
 *
 * What Sophisticated Backpacks does. EntityBackpackAdditionHandler.onLivingUpdate is registered for NeoForge's
 * EntityTickEvent.Post, so it runs for every entity on every tick of both sides; on a server level, for each living entity
 * that is not a player, it asks hasPendingBackpackAddition (monsters) and hasSpawnedJukeboxUpgrade, both
 * getPersistentData().getCompound("sophisticatedbackpacks").getBoolean(flag): for an entity without that compound each
 * getCompound builds and drops a new empty compound, and both answer false; with it, getCompound reads the key twice
 * (contains, then get). removeBackpackUuid (a monster leaving the level, EntityLeaveLevelEvent) asks hasSpawnedBackpack the
 * same way.
 *
 * What the switch does. At those three calls the same expression with one read of the key: getPersistentData() is called
 * exactly as before, once per question (NeoForge creates the compound on the same call; an entity class with its own
 * getPersistentData is asked as often as before), then the key is read once and its type id asked as getCompound's
 * contains(key, 10) asks it: nothing there or another type answers false (what the fresh empty compound answered), a
 * compound answers its own getBoolean(flag) (getCompound returns that same object). Persistent data of another class than
 * CompoundTag, or a type id 10 that is no compound, runs the mod's own expression. The answers and the data are the same;
 * no compound is built. Runtime flag bons_and_furious.sophisticatedbackpacksLivingTickFastExit.
 */
public final class SbEntityData {
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.sophisticatedbackpacksLivingTickFastExit", "true"));
    public static final String KEY = "sophisticatedbackpacks";
    public static final String PENDING = "pendingBackpackAddition", JUKEBOX = "spawnedWithJukeboxUpgrade", SPAWNED = "spawnedWithBackpack";
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    private static volatile boolean announced;

    private SbEntityData() {
    }

    /** entity.getPersistentData().getCompound("sophisticatedbackpacks").getBoolean(flag), the key read once. */
    public static boolean flag(LivingEntity entity, String flag) {
        CompoundTag data = entity.getPersistentData();
        if (data.getClass() != CompoundTag.class) return data.getCompound(KEY).getBoolean(flag);
        Tag held = data.get(KEY);
        if (held == null || held.getId() != 10) {
            if (!announced) {
                announced = true;
                LOGGER.info("Bons and Furious: sophisticatedbackpacks_living_tick_fast_exit applies (mobs without Sophisticated Backpacks data skip building an empty compound per check)");
            }
            return false;
        }
        if (held instanceof CompoundTag held10) return held10.getBoolean(flag);
        return data.getCompound(KEY).getBoolean(flag);
    }
}
