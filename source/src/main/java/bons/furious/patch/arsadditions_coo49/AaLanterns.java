package bons.furious.patch.arsadditions_coo49;

import bons.pure.config.PureConfig;
import com.hollingsworth.arsnouveau.client.registry.ModParticles;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious 1.0.49, group arsadditions_coo49 (Ars Additions 1.21.1-21.3.0 for NeoForge, client): the magelight
 * lantern's glow. Helper of AaMagelightCullMixin and AaMagelightDistanceMixin; Ars Additions is LGPL-3.0, no code of it is
 * carried.
 *
 * <p>A lit magelight lantern spawns ten glow particles every client tick (two hundred a second), each with a fresh particle
 * definition (GlowParticleData.createData: a new ColorParticleTypeData of Ars Nouveau's GLOW_TYPE), wherever it is; the game
 * drops every one of them that is more than 32 blocks from the camera (LevelRenderer.addParticleInternal), after it was
 * built and handed to the level renderer. The decision here is made once per lantern tick, at its start: the burst's spawn
 * points (the block's centre column, 0.1 to 0.3 above the block) all lie beyond the distance, so the burst is left out. The
 * lantern's own random draws still happen (the colour step, the two ParticleUtil.inRange draws per particle), so every
 * other user of those random sources sees the same numbers.
 *
 * <p>arsadditions_magelight_far_burst_skip (exact, but a trade-off, off by default since the 1.0.49 speed gate: the
 * decision and the call wrappers make a kept burst's tick slower than the stock class's, worst a near lantern on the
 * Minimal setting): the distance is the game's 32-block cull plus a quarter block, so only bursts the game would drop
 * whole are left out. It stands aside (the original runs) when the particle setting is Decreased (the game then draws a
 * level random number for every particle it is handed), when the glow particle type overrides the limiter, when a mod
 * other than Valkyrien Skies' particle transform has changed the level renderer's or client level's particle path
 * (AaParticleProbe), and for lanterns in a Valkyrien Skies shipyard (when such a build is installed).
 *
 * <p>arsadditions_magelight_particle_distance (trade-off, off by default): bursts beyond N blocks (the value, default 32)
 * are left out; below 32 a lantern between N and 32 blocks away shows no glow. Shipyard lanterns run the original. On the
 * Decreased setting it moves the level random on (the game draws one number per particle it is handed), so which of the
 * kept particles Decreased drops changes.
 *
 * <p>Runtime flags (=false: off): bons_and_furious.arsadditionsMagelightFarBurstSkip, .arsadditionsMagelightParticleDistance;
 * the distance: the "arsadditions_magelight_particle_distance.value" line or
 * -Dbons_and_furious.value.arsadditions_magelight_particle_distance=N.
 */
public final class AaLanterns {
    static final String FAR_KEY = "arsadditions_magelight_far_burst_skip";
    static final String DISTANCE_KEY = "arsadditions_magelight_particle_distance";
    public static volatile boolean farSkip = flag("arsadditionsMagelightFarBurstSkip");
    public static volatile boolean distanceSkip = flag("arsadditionsMagelightParticleDistance");
    /** The current lantern tick's decisions (set at the start of every client lantern tick, read by its particle calls). */
    public static boolean skipFar, skipDistance;
    /** Statistics (plain fields): particles left out by each switch. */
    public static long particlesSkippedFar, particlesSkippedDistance;

    static final double CULL = 32.25;

    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    private static volatile boolean announcedFar, announcedDistance;

    /** The distance switch's value, read once on its path. */
    static final class Value {
        static final int BLOCKS = PureConfig.intValue(DISTANCE_KEY, 32, 1, 1024);
    }

    private AaLanterns() {
    }

    private static boolean flag(String name) {
        return !"false".equalsIgnoreCase(System.getProperty("bons_and_furious." + name, "true"));
    }

    /** Start of a lantern tick (exact variant). Writes nothing for a server-side tick (the original returns there). */
    public static void decideFar(BlockEntity tile) {
        Level level = tile.getLevel();
        if (level == null || !level.isClientSide) return;
        skipFar = farSkip && AaParticleProbe.ok() && exactPath() && beyond(tile, CULL);
        if (skipFar && !announcedFar) {
            announcedFar = true;
            LOGGER.info("Bons and Furious: {}: magelight lanterns beyond the 32-block particle cull leave their glow bursts out", FAR_KEY);
        }
    }

    /** Start of a lantern tick (distance trade-off). */
    public static void decideDistance(BlockEntity tile) {
        Level level = tile.getLevel();
        if (level == null || !level.isClientSide) return;
        skipDistance = distanceSkip && beyond(tile, Value.BLOCKS);
        if (skipDistance && !announcedDistance) {
            announcedDistance = true;
            LOGGER.info("Bons and Furious: {}: magelight lanterns beyond {} blocks leave their glow bursts out", DISTANCE_KEY, Value.BLOCKS);
        }
    }

    private static Boolean glowForced;

    /** The game's cull applies to every particle of the burst: not on Decreased, not for a limiter-overriding type. */
    static boolean exactPath() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.options == null || mc.options.particles().get() == ParticleStatus.DECREASED) return false;
        Boolean forced = glowForced;
        if (forced == null) {
            try {
                forced = ((ParticleType<?>) ModParticles.GLOW_TYPE.get()).getOverrideLimiter();
            } catch (Throwable t) {
                forced = Boolean.TRUE;
            }
            glowForced = forced;
        }
        return !forced;
    }

    /** Every spawn point of the burst is more than the distance from the camera (and the lantern is not on a ship). */
    static boolean beyond(BlockEntity tile, double blocks) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.gameRenderer == null) return false;
        Camera camera = mc.gameRenderer.getMainCamera();
        Vec3 c = camera == null ? null : camera.getPosition();
        if (c == null) return false;
        BlockPos pos = tile.getBlockPos();
        double dx = (double) pos.getX() + 0.5 - c.x, dz = (double) pos.getZ() + 0.5 - c.z;
        double lo = (double) pos.getY() + 0.1, hi = (double) pos.getY() + 0.3, cy = c.y;
        double dy = cy < lo ? lo - cy : cy > hi ? cy - hi : 0.0;
        if (dx * dx + dy * dy + dz * dz <= blocks * blocks) return false;
        return !inShipyard(tile.getLevel(), pos);
    }

    private static volatile MethodHandle shipyard;
    private static volatile boolean shipyardLooked;

    /** Valkyrien Skies' isBlockInShipyard(Level, BlockPos), when the mod is present (a lantern on a ship: the original). */
    static boolean inShipyard(Level level, BlockPos pos) {
        if (!shipyardLooked) {
            try {
                Class<?> utils = Class.forName("org.valkyrienskies.mod.common.VSGameUtilsKt");
                shipyard = MethodHandles.publicLookup().findStatic(utils, "isBlockInShipyard", MethodType.methodType(boolean.class, Level.class, BlockPos.class));
            } catch (Throwable t) {
                shipyard = null;
            }
            shipyardLooked = true;
        }
        MethodHandle h = shipyard;
        if (h == null) return false;
        try {
            return (boolean) h.invokeExact(level, pos);
        } catch (Throwable t) {
            return true;
        }
    }
}
