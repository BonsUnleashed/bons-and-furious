package bons.furious.patch.summonity_coo49;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch summonity_drone_modifier_id_memo (Summonity 0.1.7.1, server side). Ours; no Summonity code is
 * carried. Idea: Collections Of Optimizations (summonity.memoDroneModifierIds), idea text only.
 *
 * SummonedCopperDroneEntity.modifierId(bonus) is UUID.nameUUIDFromBytes((getUUID() + ":" + bonus) in UTF-8): a string
 * build, an MD5 digest and a new UUID, asked twice per drone per tick (armor and speed, in applySupportBonus) and when the
 * bonus is removed. It depends only on the drone's UUID and the bonus name, so the two answers are kept per drone
 * (SummonityDroneIdMixin) with the UUID object they were made from and handed out again while getUUID() returns that same
 * object; a drone whose UUID changes (setUUID) gets new answers. The kept UUIDs are equal to new ones (UUID equality,
 * hash, string and saved form); callers use them as attribute modifier ids, which compare by equals. Flag:
 * -Dbons_and_furious.summonityDroneModifierIdMemo=false; shadow (computes the id as shipped too and compares):
 * -Dbons_and_furious.summonityDroneModifierIdMemo.shadow=true.
 */
public final class DroneIds {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.summonityDroneModifierIdMemo", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.summonityDroneModifierIdMemo.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong();
    public static final AtomicLong SHADOW_MISMATCHES = new AtomicLong();
    public static final AtomicLong REUSES = new AtomicLong();
    private static volatile boolean announced;

    private DroneIds() {
    }

    /** Called when a kept id is handed out (counts, announces once, shadow compare). */
    public static void reused(UUID kept, String bonus, Operation<UUID> original) {
        REUSES.incrementAndGet();
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: summonity_drone_modifier_id_memo applies (copper drones keep their two modifier ids)");
        }
        if (SHADOW) {
            UUID fresh = original.call(bonus);
            SHADOW_CHECKS.incrementAndGet();
            if (!kept.equals(fresh) && SHADOW_MISMATCHES.incrementAndGet() <= 20) {
                LOGGER.warn("Bons and Furious: summonity_drone_modifier_id_memo shadow mismatch for {}: kept {}, computed {}", bonus, kept, fresh);
            }
        }
    }
}
