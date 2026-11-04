package bons.furious.patch.brutality_coo49;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicLong;
import net.goo.brutality.entity.capabilities.EntityCapabilities;
import net.goo.brutality.registry.BrutalityCapabilities;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch brutality_blight_scan_idle_skip (Brutality 0.8.5, MIT; client; since 1.0.49). Idea: Collections
 * Of Optimizations (brutality.skipIdleBlightScan), idea text only.
 *
 * ForgeClientParticleHandler.onPlayerTick (no phase check, so twice per client tick) calls spawnMiracleBlightParticles,
 * which searches every LivingEntity within 50 blocks of the player (Level.getEntitiesOfClass: the profiler's
 * "getEntities" counter, then the section walk) and asks each one's Brutality effect capability; only a capability whose
 * miracleBlighted flag is set draws from the level's random source and spawns particles.
 *
 * What changes: this class keeps a weak identity set of every EntityEffectCap whose flag is set (BrutalityEffectCapWatchMixin
 * updates it at the return of setMiracleBlighted and deserializeNBT, the only code that writes the private field; the
 * capability's other code, including Brutality's clone and sync paths, goes through them). While the set is empty no
 * capability anywhere in this JVM is blighted, so the search would find no entity to act on: the method then counts the
 * profiler's "getEntities" (what the search does first) and returns, with no walk, no capability lookups, no random draw
 * and no particle, which is what the search would have ended in. While any capability is blighted (on any side, near or
 * far) the method runs as shipped. A capability stays in the set while it is reachable; a blighted capability of an
 * entity that is gone only keeps the shipped path running until it is collected.
 *
 * -Dbons_and_furious.brutalityBlightScanIdleSkip=false runs the method as shipped; shadow mode
 * (-Dbons_and_furious.brutalityBlightScanIdleSkip.shadow=true) never skips: whenever it would, it repeats the search and
 * checks that no result is blighted (SHADOW_CHECKS / SHADOW_MISMATCHES, at most 20 WARN lines).
 */
public final class BrutalityBlight {
    public static final String KEY = "brutality_blight_scan_idle_skip";
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.brutalityBlightScanIdleSkip", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.brutalityBlightScanIdleSkip.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong();
    public static final AtomicLong SHADOW_MISMATCHES = new AtomicLong();
    /** Every EntityEffectCap whose miracleBlighted flag is set, by identity (the class keeps Object's equals / hashCode). */
    private static final Map<Object, Boolean> BLIGHTED = new WeakHashMap<>();
    private static volatile boolean announced;

    private BrutalityBlight() {
    }

    /** At the return of EntityEffectCap.setMiracleBlighted / deserializeNBT: the capability's flag as it is now. */
    public static void watch(Object cap, boolean blighted) {
        synchronized (BLIGHTED) {
            if (blighted) BLIGHTED.put(cap, Boolean.TRUE);
            else BLIGHTED.remove(cap);
        }
    }

    /** True while some reachable capability has its flag set. */
    public static boolean anyBlighted() {
        synchronized (BLIGHTED) {
            return !BLIGHTED.isEmpty();
        }
    }

    /** At the head of spawnMiracleBlightParticles: true = the search can act on nothing; counted and skipped. */
    public static boolean skip(Player player, Level level) {
        if (!enabled || anyBlighted()) return false;
        if (SHADOW) {
            shadow(player, level);
            return false;
        }
        level.m_46473_().m_6174_("getEntities");
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: {} applies (Brutality's miracle blight particle search is left out while no entity is blighted)", KEY);
        }
        return true;
    }

    private static void shadow(Player player, Level level) {
        List<LivingEntity> found = level.m_45976_(LivingEntity.class, player.m_20191_().m_82400_(50.0));
        for (LivingEntity e : found) {
            SHADOW_CHECKS.incrementAndGet();
            Object cap = e.getCapability(BrutalityCapabilities.ENTITY_EFFECT_CAP).orElse(null);
            boolean blighted = cap instanceof EntityCapabilities.EntityEffectCap c && c.isMiracleBlighted();
            if (blighted && SHADOW_MISMATCHES.incrementAndGet() <= 20) {
                LOGGER.warn("Bons and Furious: {} shadow mismatch: {} is blighted while the watch set was empty", KEY, e);
            }
        }
    }
}
