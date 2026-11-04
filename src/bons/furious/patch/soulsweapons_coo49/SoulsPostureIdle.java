package bons.furious.patch.soulsweapons_coo49;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.entity.LivingEntity;
import net.soulsweaponry.entitydata.PostureData;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch soulsweapons_idle_posture_max_skip (Soulslike Weaponry 1.4.10, same bytes in 1.4.8; CC0-1.0; both
 * sides; since 1.0.49). Trade-off, off by default. Ours; no Soulslike Weaponry code is carried. Idea: Collections Of
 * Optimizations (soulsweapons.skipIdlePostureMax), idea text only.
 *
 * ModEvents.onLivingEntityTicks reads the entity's posture (PostureData.getPosture), its bleed and frost values and then
 * tests "posture >= getMaxPostureLoss(e) && getMaxPostureLoss(e) != 0". getMaxPostureLoss works the maximum out from the
 * entity's stats, its BASE_POSTURE_INCREASE attribute (created lazily on the first ask) and its scaled hitbox; for a
 * server player it also writes the result into the player's persistent data and sends a MaxPostureSyncS2C packet, so every
 * server player costs one NBT write and one packet per tick.
 *
 * maxPostureLoss answers Integer.MAX_VALUE without the computation while the entity's posture (read again with the same
 * PostureData.getPosture: the key already exists, nothing between the two reads writes it) is 0 or below; then
 * "posture >= max" is false and the second call is skipped, as in the original whenever the maximum exceeds the posture.
 * Otherwise the call runs as shipped. What changes (documented trade-off): a server player's maximum posture is written and
 * sent when posture is added (PostureData.addPostureLoss asks getMaxPostureLoss itself) instead of every tick; an entity's
 * BASE_POSTURE_INCREASE attribute instance is created on the first posture hit instead of on its first tick (when the
 * maximum reads it: stats with a max_posture_loss of 0, or none); and an entity whose BASE_POSTURE_INCREASE is so negative
 * that its computed maximum falls below 0 is no longer broken on every tick at 0 posture. (A negative max_posture_loss in
 * the stats switches posture off for that entity in isPostureDisabled, before any of this: nothing changes there.)
 * Runtime flag -Dbons_and_furious.soulsweaponsIdlePostureMaxSkip=false (the config switch decides whether the hook is
 * applied).
 */
public final class SoulsPostureIdle {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.soulsweaponsIdlePostureMaxSkip", "true"));
    /** Ticks answered without the maximum (statistics; plain, racy). */
    public static long skipped;
    private static volatile boolean announced;

    private SoulsPostureIdle() {
    }

    /** In place of the first EntityPosture.getMaxPostureLoss(entity) call of onLivingEntityTicks. */
    public static int maxPostureLoss(LivingEntity entity, Operation<Integer> original) {
        if (enabled && PostureData.getPosture(entity) <= 0) {
            skipped++;
            if (!announced) announce();
            return Integer.MAX_VALUE;
        }
        return original.call(entity);
    }

    private static void announce() {
        announced = true;
        LOGGER.info("Bons and Furious: soulsweapons_idle_posture_max_skip applies (Soulslike Weaponry skips the maximum posture of entities at 0 posture)");
    }
}
