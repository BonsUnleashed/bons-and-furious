package bons.furious.patch.refinedmod_coo49;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switches of group refinedmod_coo49 (Refined Mod / "RefinedRain's Spells" 1.1.6, All Rights Reserved: our
 * own logic only, nothing of the mod carried; since 1.0.49). Ideas: Collections Of Optimizations (refinedmod.*), idea text
 * only.
 *
 * refinedmod_hunters_mark_bonus_floor (change, gameplay fix, off by default; both sides): HuntersMarkHandler.getBonusModifier
 * answers ((amplifier - 1) x spell power + 2) x 0.1, which is negative for a level 1 mark (amplifier 0) once the caster's
 * spell power is above 2, so the mark then LOWERS the marked hits' damage (and the spell's description shows a negative
 * bonus). With the switch a negative answer becomes 0; every other answer (NaN included) is returned unchanged.
 *
 * refinedmod_storm_blade_ownerless_discard (change, gameplay fix, off by default; server): StormBladeCollider.onHitEntity
 * broadcasts its impact particles and then returns WITHOUT removing the collider when its caster is gone (logged out,
 * unloaded, in another dimension): the blade flies on through entities, re-hitting and sparking every tick until its
 * 20-tick lifetime ends, and can never hurt anything (damage needs the caster). With the switch such a collider is
 * discarded at its first hit, before the particles.
 *
 * refinedmod_wave_terrain_server_only (fix, on; client): CustomWaveEntity.tick runs extinguishNearbyFires (fire within 1
 * block -> air) and convertLavaToCobblestone (lava within 2 blocks -> cobblestone) on both sides with setBlock: the client
 * writes those blocks into its own world at its interpolated wave position, which can differ from the server's, so made-up
 * air or cobblestone stays on the client until the block is next updated. With the switch the two sweeps return at once
 * on a client level; the server's own sweeps reach the client as block updates.
 *
 * refinedmod_wave_terraforming_off (change, off by default; both sides): the two sweeps return at once on every level
 * (an admin option: tidal waves no longer clear fire or turn lava into cobblestone; their damage is unchanged).
 *
 * refinedmod_wave_terrain_scan_lean (change, off by default; both sides): a wave moves 0.15 blocks a tick and lives 10-16
 * ticks, so most of its sweeps re-read the cube the previous one read (and already cleared). With the switch each sweep
 * runs again only once the wave has entered a new block since that sweep last ran to its end. Difference: fire or lava
 * that appears in the cube while the wave stays in the same block (spreading fire, lava flowing or placed there) is left
 * until the wave moves on, or for good if it never does.
 *
 * Runtime flags (emergency off): bons_and_furious.refinedmodHuntersMarkBonusFloor, .refinedmodStormBladeOwnerlessDiscard,
 * .refinedmodWaveTerrainServerOnly, .refinedmodWaveTerraformingOff, .refinedmodWaveTerrainScanLean.
 */
public final class RefinedFixes {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean bonusFloor = flag("refinedmodHuntersMarkBonusFloor");
    public static volatile boolean ownerlessDiscard = flag("refinedmodStormBladeOwnerlessDiscard");
    public static volatile boolean terrainServerOnly = flag("refinedmodWaveTerrainServerOnly");
    public static volatile boolean terraformingOff = flag("refinedmodWaveTerraformingOff");
    public static volatile boolean leanTerrainScan = flag("refinedmodWaveTerrainScanLean");
    private static volatile boolean bonusAnnounced, ownerAnnounced, terrainAnnounced, offAnnounced, leanAnnounced;

    private RefinedFixes() {
    }

    private static boolean flag(String name) {
        return !"false".equalsIgnoreCase(System.getProperty("bons_and_furious." + name, "true"));
    }

    /** The answer of HuntersMarkHandler.getBonusModifier: a negative bonus becomes 0. */
    public static float bonus(float answer) {
        if (!bonusFloor || !(answer < 0.0f)) return answer;
        if (!bonusAnnounced) {
            bonusAnnounced = true;
            LOGGER.info("Bons and Furious: refinedmod_hunters_mark_bonus_floor applies (a Hunter's Mark never lowers the marked hits' damage)");
        }
        return 0.0f;
    }

    /** At the head of StormBladeCollider.onHitEntity: true = the collider had no living caster and was discarded. */
    public static boolean discardOwnerless(Projectile collider) {
        if (!ownerlessDiscard || collider.m_9236_().f_46443_) return false;
        Entity owner = collider.m_19749_();
        if (owner instanceof LivingEntity) return false;
        collider.m_146870_();
        if (!ownerAnnounced) {
            ownerAnnounced = true;
            LOGGER.info("Bons and Furious: refinedmod_storm_blade_ownerless_discard applies (a storm blade whose caster is gone vanishes at its first hit)");
        }
        return true;
    }

    /** The block a CustomWaveEntity sweep is centred on, as the sweep computes it (floor of the position), packed. */
    public static long sweepBlock(Entity wave) {
        return BlockPos.m_121882_((int) Math.floor(wave.m_20185_()), (int) Math.floor(wave.m_20186_()), (int) Math.floor(wave.m_20189_()));
    }

    /** At the head of a CustomWaveEntity sweep that already ran to its end: true = the wave is still in that sweep's block. */
    public static boolean sameBlock(Entity wave, long lastBlock) {
        if (!leanTerrainScan || sweepBlock(wave) != lastBlock) return false;
        if (!leanAnnounced) {
            leanAnnounced = true;
            LOGGER.info("Bons and Furious: refinedmod_wave_terrain_scan_lean applies (a tidal wave sweeps fire and lava again only when it enters a new block)");
        }
        return true;
    }

    /** At the head of each CustomWaveEntity terrain sweep: true = the sweep returns at once. */
    public static boolean skipSweep(Level level, boolean clientOnlyKey) {
        if (clientOnlyKey) {
            if (!terrainServerOnly || level == null || !level.f_46443_) return false;
            if (!terrainAnnounced) {
                terrainAnnounced = true;
                LOGGER.info("Bons and Furious: refinedmod_wave_terrain_server_only applies (tidal waves change blocks on the server only)");
            }
            return true;
        }
        if (!terraformingOff) return false;
        if (!offAnnounced) {
            offAnnounced = true;
            LOGGER.info("Bons and Furious: refinedmod_wave_terraforming_off applies (tidal waves no longer clear fire or turn lava into cobblestone)");
        }
        return true;
    }
}
