package bons.furious.patch.photon_c49;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch photon_particle_light_pos_reuse (Photon 1.1.17; client). Ours; no Photon code is carried.
 *
 * What Photon does. Every tick, every Photon tile particle without a lights module or bloom looks its light up again:
 * TileParticle.getLightColor turns the particle's world position into new BlockPos((int) x, (int) y, (int) z) and hands it
 * to its emitter, whose getLightColor(BlockPos) answers from a per-tick light cache keyed by that position. A particle that
 * stays inside one block builds an equal BlockPos every tick (24 bytes each).
 *
 * What the switch does. Each particle keeps the BlockPos it built last; when the three truncated coordinates (the same
 * (int) conversions, computed by Photon's own code) equal it, that BlockPos is handed over again, otherwise a new one is
 * built and kept. The light is still looked up every tick. Exact variant: the emitter may receive the same BlockPos instance
 * on consecutive ticks (an immutable position equal to the one stock builds; Photon's emitter only uses it as a key of its
 * light cache and to read the level's light).
 *
 * -Dbons_and_furious.photonParticleLightPosReuse=false switches it off at run time.
 */
public final class PhotonLightPos {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.photonParticleLightPosReuse", "true"));
    private static volatile boolean announced;

    private PhotonLightPos() {}

    /** Called when a particle hands its kept BlockPos over again: one INFO line the first time. */
    public static void reused() {
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: photon_particle_light_pos_reuse applies (Photon particles reuse their light position while they stay in one block)");
        }
    }
}
