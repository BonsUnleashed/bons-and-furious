package bons.furious.patch.projectile_gates;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.util.profiling.InactiveProfiler;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch immersiveaircraft_vehicle_trace_gate (Immersive Aircraft 1.1.8, GPL-3.0, both sides, since
 * 1.0.46), and the layer access shared with knightlib_bone_hitbox_gate (BoneHitboxPickGate). Helper of the projectile_gates
 * mixins; no code of Immersive Aircraft, KnightLib or Minecraft is carried, only our own decisions.
 *
 * <p>At the RETURN of both ProjectileUtil.getEntityHitResult overloads (every projectile's hit test each tick; the client's
 * crosshair pick each frame) Immersive Aircraft's handler searches the entities in the hit-test box inflated by 16 blocks in
 * every direction with the predicate {@code VehicleEntity.class::isInstance}, then tests each vehicle found against the
 * projectile's filter and its extra shapes. While the level holds no VehicleEntity, that search can only return an empty
 * list: its candidates are the level's section entities (vanilla_entity_class_count_layer counts every VehicleEntity there,
 * subclasses included: Immersive Aircraft, Immersive Machinery, Man of Many Planes), Forge's part entities (a PartEntity is
 * never a VehicleEntity: both extend Entity directly) and the sub-parts Aquamirae 7.1.14 adds to that search (its
 * SubPartEntity also extends Entity directly). Then the handler's loop does nothing, calls no filter, and the hit result
 * stands exactly as Minecraft and the other handlers left it. The gate returns from the handler at its head in that case.
 * What the skipped search would have done besides finding nothing is kept identical by the gate's own conditions: the
 * profiler's "getEntities" counter (the gate acts only while the level's profiler is Minecraft's InactiveProfiler, where the
 * counter does nothing), and the walk cannot fail (box coordinates within +-30,000,000 and at most 1,024 blocks wide, so
 * 1.20.1's section keys never wrap; Valkyrien Skies 2.4.11's too-big-box test (volume > 1e8) never fires and its ship-section
 * walk, whose shipyards lie within +-28.7 million blocks, cannot throw). With a vehicle anywhere in the level, a level whose
 * search is not vanilla's, an active profiler, another thread, or counts the layer is not sure of, Immersive Aircraft's
 * handler runs as shipped.
 *
 * <p>Requires vanilla_entity_class_count_layer (group crittersandcompanions_c2) with Immersive Aircraft's VehicleEntity and
 * KnightLib's BoneHitboxHolder among its tracked types (1.0.46 layer edit), reached through constant method handles so this
 * group compiles on its own; with that switch off, or without those entries, the layer answers "unknown" and the handlers
 * run. -Dbons_and_furious.immersiveaircraftVehicleTraceGate=false turns the gate off at runtime. SHADOW MODE for rigs:
 * -Dbons_and_furious.immersiveaircraftVehicleTraceGate.shadow=true never skips; where the gate would, it runs Immersive
 * Aircraft's own search (same box, same predicate) and counts SHADOW_CHECKS / SHADOW_MISMATCHES (a vehicle found; WARN for
 * the first 20), then the handler runs as shipped.
 */
public final class ProjectileGates {
    /** immersiveaircraft_vehicle_trace_gate's runtime switch. */
    public static volatile boolean vehicleTrace = flag("immersiveaircraftVehicleTraceGate");
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.immersiveaircraftVehicleTraceGate.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    /** Statistics for probes and the rig (plain field; written by the thread that owns the level). */
    public static long skipped;

    static final String VEHICLE = "immersive_aircraft.entity.VehicleEntity";
    static final String HOLDER = "dev.xylonity.knightlib.api.entity.hitbox.BoneHitboxHolder";
    static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    private static final String LAYER = "bons.furious.patch.crittersandcompanions_c2.EntityClassCounts";
    /** EntityClassCounts.none(Level, int), or null when the layer is absent. */
    private static final MethodHandle NONE;
    /** The layer's indices of VehicleEntity and BoneHitboxHolder, or -1 when the layer does not track them. */
    static final int VEHICLE_INDEX, HOLDER_INDEX;
    /** Box limits: the search walk cannot wrap 1.20.1's section keys (2^21 sections = 33,554,432 blocks). */
    private static final double COORD_LIMIT = 3.0E7, WIDTH_LIMIT = 1024.0, INFLATE = 16.0;
    private static volatile boolean announced;

    static {
        MethodHandle none = null;
        int vehicle = -1, holder = -1;
        try {
            Class<?> counts = Class.forName(LAYER, false, ProjectileGates.class.getClassLoader());
            MethodHandles.Lookup l = MethodHandles.lookup();
            none = l.findStatic(counts, "none", MethodType.methodType(boolean.class, Level.class, int.class));
            MethodHandle tracked = l.findStatic(counts, "trackedIndex", MethodType.methodType(int.class, String.class));
            vehicle = (int) tracked.invokeExact(VEHICLE);
            holder = (int) tracked.invokeExact(HOLDER);
        } catch (Throwable t) {
            none = null;
            LOGGER.warn("Bons and Furious: immersiveaircraft_vehicle_trace_gate and knightlib_bone_hitbox_gate cannot reach "
                    + "vanilla_entity_class_count_layer ({}); Immersive Aircraft's and KnightLib's searches run unchanged", t.toString());
        }
        NONE = none;
        VEHICLE_INDEX = vehicle;
        HOLDER_INDEX = holder;
        if (NONE != null && (vehicle < 0 || holder < 0))
            LOGGER.warn("Bons and Furious: vanilla_entity_class_count_layer does not track {}{}{}; the gate(s) concerned stay off",
                    vehicle < 0 ? VEHICLE : "", vehicle < 0 && holder < 0 ? " and " : "", holder < 0 ? HOLDER : "");
    }

    private ProjectileGates() {
    }

    static boolean flag(String name) {
        return !"false".equalsIgnoreCase(System.getProperty("bons_and_furious." + name, "true"));
    }

    /** The layer's answer for tracked type {@code index} in {@code level}: true = certainly none; false = unknown. */
    static boolean none(Level level, int index) {
        if (NONE == null || index < 0) return false;
        try {
            return (boolean) NONE.invokeExact(level, index);
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * True when Immersive Aircraft's vehicle search for the hit-test box {@code box} in {@code level} is certain to find
     * nothing and to have no other effect, so its handler may return at once. Called at the head of both handlers.
     */
    public static boolean skipVehicleTrace(Level level, AABB box) {
        if (!vehicleTrace || level == null || box == null) return false;
        if (level.m_46473_() != InactiveProfiler.f_18554_) return false;   // an active profiler would count "getEntities"
        double x0 = box.f_82288_ - INFLATE, y0 = box.f_82289_ - INFLATE, z0 = box.f_82290_ - INFLATE;
        double x1 = box.f_82291_ + INFLATE, y1 = box.f_82292_ + INFLATE, z1 = box.f_82293_ + INFLATE;
        // !(a <= b) also rejects NaN; bounded coordinates are finite
        if (!(Math.abs(x0) <= COORD_LIMIT && Math.abs(x1) <= COORD_LIMIT && Math.abs(y0) <= COORD_LIMIT && Math.abs(y1) <= COORD_LIMIT
                && Math.abs(z0) <= COORD_LIMIT && Math.abs(z1) <= COORD_LIMIT)) return false;
        if (!(x1 - x0 <= WIDTH_LIMIT && y1 - y0 <= WIDTH_LIMIT && z1 - z0 <= WIDTH_LIMIT)) return false;
        if (!none(level, VEHICLE_INDEX)) return false;
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: immersiveaircraft_vehicle_trace_gate: Immersive Aircraft's vehicle search after each projectile hit test "
                    + "is skipped while the level holds no vehicle{}", SHADOW ? " (SHADOW MODE: the search runs every time and is compared)" : "");
        }
        if (SHADOW) {
            SHADOW_CHECKS.incrementAndGet();
            List<Entity> found = level.m_6249_(null, box.m_82400_(INFLATE), ProjectileGates::isVehicle);
            if (!found.isEmpty() && SHADOW_MISMATCHES.incrementAndGet() <= 20) {
                LOGGER.warn("Bons and Furious: immersiveaircraft_vehicle_trace_gate SHADOW MISMATCH {}: the layer says {} holds no vehicle, the search found {}",
                        SHADOW_MISMATCHES.get(), level, found.get(0));
            }
            return false;
        }
        skipped++;
        return true;
    }

    private static final ClassValue<Boolean> VEHICLE_CLASS = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            for (Class<?> c = type; c != null; c = c.getSuperclass()) if (c.getName().equals(VEHICLE)) return true;
            return false;
        }
    };

    /** Shadow mode only: Immersive Aircraft's predicate, by class name (this group does not link against Immersive Aircraft). */
    static boolean isVehicle(Entity e) {
        return VEHICLE_CLASS.get(e.getClass());
    }
}
