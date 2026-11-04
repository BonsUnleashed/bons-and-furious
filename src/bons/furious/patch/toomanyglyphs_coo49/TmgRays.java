package bons.furious.patch.toomanyglyphs_coo49;

import bons.pure.config.PureConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious 1.0.49, group toomanyglyphs_coo49 (Too Many Glyphs 1.20.1-2.3.2.12345; client). Helper of the group's
 * mixin; Too Many Glyphs is LGPL-2.1+, no code of it is carried (the budget is our own logic).
 *
 * <p>toomanyglyphs_ray_particle_budget (trade-off, off by default): PacketRayEffect$ClientHandler.accept draws a Ray or
 * Chaining beam as one Ars Nouveau glow particle every 1/16 block (256 for a 16 block ray), and a Chaining cast sends one
 * beam per link in the same tick, so one cast can ask for thousands of particles at once. With the switch on, a beam is
 * still drawn exactly as the mod draws it, but once the beams of one client tick have handed out the budget (the value,
 * default 1024 particles), the beams still to come in that tick are not drawn at all. What a player can notice: the tail of
 * a very large burst is missing; the spell itself (server side) is unchanged. The particles are counted at the mod's own
 * ClientLevel.addParticle calls; the tick is the client level's game time (beams arrive as queued packet work on the
 * client thread between level ticks).
 *
 * <p>Runtime flag (emergency off): bons_and_furious.toomanyglyphsRayParticleBudget=false. The value: the
 * "toomanyglyphs_ray_particle_budget.value" line, or -Dbons_and_furious.value.toomanyglyphs_ray_particle_budget=N.
 */
public final class TmgRays {
    static final String KEY = "toomanyglyphs_ray_particle_budget";
    public static volatile boolean budget = flag("toomanyglyphsRayParticleBudget");
    /** Statistics (client thread): beams drawn and skipped, particles handed out this tick. */
    public static long raysDrawn, raysSkipped;
    static long tick = Long.MIN_VALUE;
    static int spent;

    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    private static boolean announced;

    /** The budget per client tick, read once on the switch-on path. */
    static final class Value {
        static final int BUDGET = PureConfig.intValue(KEY, 1024, 1, 1000000);
    }

    private TmgRays() {
    }

    private static boolean flag(String name) {
        return !"false".equalsIgnoreCase(System.getProperty("bons_and_furious." + name, "true"));
    }

    /** The HEAD of accept: true = skip this beam (the tick's budget is spent). */
    public static boolean skipRay() {
        if (!budget) return false;
        ClientLevel level = Minecraft.m_91087_().f_91073_;
        if (level == null) return false;                     // the handler returns at once itself
        long now = level.m_46467_();
        if (now != tick) {
            tick = now;
            spent = 0;
        }
        if (spent >= Value.BUDGET) {
            raysSkipped++;
            if (!announced) {
                announced = true;
                LOGGER.info("Bons and Furious: {}: Too Many Glyphs beams beyond {} particles in one client tick are not drawn", KEY, Value.BUDGET);
            }
            return true;
        }
        raysDrawn++;
        return false;
    }

    /** One particle the beam handed to ClientLevel.addParticle. */
    public static void counted() {
        spent++;
    }

    /** Offline proof seam: a fresh client tick state. */
    public static void resetForProof() {
        tick = Long.MIN_VALUE;
        spent = 0;
    }
}
