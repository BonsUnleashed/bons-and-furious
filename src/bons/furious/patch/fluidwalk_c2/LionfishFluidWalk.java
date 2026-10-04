package bons.furious.patch.fluidwalk_c2;

import com.github.L_Ender.lionfishapi.server.event.StandOnFluidEvent;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch lionfishapi_fluid_walk_scan (Lionfish API 2.8 and 3.0: byte-identical EntityMixin and
 * StandOnFluidEvent; both sides).
 *
 * Lionfish API's EntityMixin.fluidCollision (a @ModifyVariable on Entity.move after collide(), run for every living
 * entity that is not moving up) built 13 double arrays and 12 BlockPos per call, about 0.8 KB, to probe the cells around
 * the entity for a fluid it can stand on, then posted StandOnFluidEvent for the best one. {@link #fluidCollision} gives the
 * identical answer with no allocation: FluidWalkScan's loop reads the same cells in the same order (the 6 repeated cells
 * only where a repeat is provably a pure cache hit, see FluidWalkScan) and then does exactly what the handler did with the
 * result: no fluid -> the original vector; otherwise one StandOnFluidEvent for the entity and the best fluid, and when a
 * listener cancels it fallDistance = 0, setOnGround(true) and the vector with the best height. Lionfish API is LGPL; the
 * code here is our own.
 */
public final class LionfishFluidWalk {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    /** Runtime switch. -Dbons_and_furious.lionfishFluidWalkScan=false hands every call back to Lionfish's own code. */
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.lionfishFluidWalkScan", "true"));
    /** -Dbons_and_furious.lionfishFluidWalkScan.shadow=true: also run the original loop shape (no event) and compare. */
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.lionfishFluidWalkScan.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong();
    public static final AtomicLong SHADOW_MISMATCHES = new AtomicLong();
    private static final double[][] ORIGINAL_OFFSETS = {{0.5, 0.0, 0.5}, {0.5, 0.0, 0.0}, {0.5, -1.0, 0.0}, {0.5, 0.0, -0.5},
            {0.0, 0.0, 0.5}, {0.0, 0.0, 0.0}, {0.0, -1.0, 0.0}, {0.0, 0.0, -0.5}, {-0.5, 0.0, 0.5}, {-0.5, 0.0, 0.0},
            {-0.5, -1.0, 0.0}, {-0.5, 0.0, -0.5}};
    private static volatile boolean announced;

    private LionfishFluidWalk() {
    }

    /** The handler's result for a living entity (the mixin passes non-living entities on to the original code). */
    public static Vec3 fluidCollision(LivingEntity entity, Vec3 original, FluidWalkCursor cursor) {
        if (!announced) announce();
        if (original.f_82480_ > 0.0) return original;
        Level level = entity.m_20193_();
        FluidState highestFluid = FluidWalkScan.scan(entity, level, original.f_82480_, cursor, FluidWalkScan.LIONFISH_DX,
                FluidWalkScan.LIONFISH_DY, FluidWalkScan.LIONFISH_DZ, FluidWalkScan.LIONFISH_REPEAT);
        double highestValue = cursor.highestValue;
        if (SHADOW) shadow(entity, level, original.f_82480_, highestFluid, highestValue);
        if (highestFluid == null) return original;
        StandOnFluidEvent event = new StandOnFluidEvent(entity, highestFluid);
        MinecraftForge.EVENT_BUS.post(event);
        if (event.isCanceled()) {
            entity.f_19789_ = 0.0f;
            entity.m_6853_(true);
            return new Vec3(original.f_82479_, highestValue, original.f_82481_);
        }
        return original;
    }

    private static void shadow(LivingEntity entity, Level level, double start, FluidState fluid, double value) {
        double[] out = new double[1];
        FluidState ref = FluidWalkScan.reference(entity, level, start, ORIGINAL_OFFSETS, out);
        SHADOW_CHECKS.incrementAndGet();
        if (ref != fluid || Double.doubleToRawLongBits(out[0]) != Double.doubleToRawLongBits(value)) {
            long n = SHADOW_MISMATCHES.incrementAndGet();
            if (n <= 20) LOGGER.warn("Bons and Furious: lionfishapi_fluid_walk_scan shadow mismatch #{} for {} at {}: ours {} / {}, original loop {} / {}",
                    n, entity, entity.m_20183_(), fluid, value, ref, out[0]);
        }
    }

    private static synchronized void announce() {
        if (announced) return;
        announced = true;
        LOGGER.info("Bons and Furious: lionfishapi_fluid_walk_scan: Lionfish API's fluid-walk check reads its cells without allocating{}",
                SHADOW ? " (shadow check on)" : "");
    }
}
