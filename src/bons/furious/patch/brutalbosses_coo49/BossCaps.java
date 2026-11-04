package bons.furious.patch.brutalbosses_coo49;

import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch brutalbosses_living_boss_capability (Brutal Bosses 8.5, All Rights Reserved; client side of an
 * entity listener that runs on both sides; exact variant). Ours; no Brutal Bosses code is carried. Idea: Collections Of
 * Optimizations (brutalbosses.skipNonLivingBossCapability), idea text only.
 *
 * Brutal Bosses' attachCapabilities runs for every entity object built. On a client level it attaches a BossCapability (a
 * new object with a LazyOptional and a lambda, plus the event's map entry; for an entity without any other capability
 * Forge then also builds a capability dispatcher) to EVERY entity: dropped items, experience orbs, arrows and so on. With
 * the switch on, a non-living entity on a client level gets none. A boss is always a living mob (bosses are spawned as
 * Mob, loaded as LivingEntity, set up in FinalizeSpawn), the server never sends boss data for anything else, and every
 * client reader of BOSS_CAP either tests for a living entity first or treats an absent capability like a non-boss one.
 * Exact variant: the one internal difference is that non-living client entities answer getCapability(BOSS_CAP) empty
 * instead of with a never-a-boss capability. Server levels run the original (the server's decision, and so saved data,
 * stay the mod's own). Flag: -Dbons_and_furious.brutalbossesLivingBossCapability=false.
 */
public final class BossCaps {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.brutalbossesLivingBossCapability", "true"));
    /** For the harness: client attaches left out. */
    public static final AtomicLong CLIENT_SKIPS = new AtomicLong();
    private static volatile boolean announced;

    private BossCaps() {
    }

    /** True when the listener would only attach a capability to a non-living client entity. */
    public static boolean skipOnClient(AttachCapabilitiesEvent<Entity> event) {
        if (!enabled) return false;
        Object o = event.getObject();
        if (!(o instanceof Entity e) || o instanceof LivingEntity) return false;
        Level level = e.m_9236_();
        if (level == null || !level.f_46443_) return false;
        CLIENT_SKIPS.incrementAndGet();
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: brutalbosses_living_boss_capability applies (no boss capability on non-living client entities)");
        }
        return true;
    }
}
