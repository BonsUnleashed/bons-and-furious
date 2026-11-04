package bons.furious.patch.l2hostility_plate_c49;

import dev.xkmc.l2library.util.raytrace.RayTraceUtil;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch l2hostility_name_plate_hover_direct (L2 Hostility 2.5.19 with L2 Library 2.5.x, client). L2
 * Hostility is All Rights Reserved: none of its code is carried. L2 Library is LGPL-2.1: trace() repeats the three calls of
 * its RayTraceUtil.rayTraceEntity (guarded) with values it has already computed. Ours, idea from Collections Of
 * Optimizations (l2hostility.namePlateSightPerTick), idea text only.
 *
 * What L2 Hostility does: ClientEvents.renderNamePlate draws the level / trait lines over every rendered mob that carries
 * its capability. When the mob is invisible, or with its "show only when hovered" option, it first asks L2 Library's
 * RayTraceUtil.rayTraceEntity(player, reach, e -> e == mob) and draws nothing when that answers null: an entity search
 * along the whole look ray (Level.getEntities over the sections the ray's box touches) and a box clip per entity found,
 * every frame for every such mob, only to learn whether that one mob is under the crosshair.
 *
 * What the switch does (NamePlateHoverMixin, a @Redirect of that call): the predicate admits only the mob itself, and the
 * vanilla trace (ProjectileUtil.getEntityHitResult with inflate 0.3F) answers non-null only for a candidate whose box grown
 * by (double) 0.3F is clipped by the ray from the player's eye to RayTraceUtil.getRayTerm's end; AABB.clip accepts points up
 * to 1e-7 outside the box. So when the ray does not meet the grown box enlarged by 1e-4 (a separating-axis test of the
 * segment against that box, our own arithmetic: no division, no allocation), the answer is null without the search, and
 * the "getEntities" profiler counter the search would have incremented is incremented. Otherwise the shipped search runs,
 * as rayTraceEntity makes it, from the eye position and ray end already computed (it may still answer null, e.g. for a mob
 * that is no longer in the level). The mob under the game's own crosshair skips the test and gets the shipped call at once.
 * Exact for vanilla's search and every hook on it that only adds candidates that pass the
 * predicate (Valkyrien Skies' ship-section walk and Aquamirae's sub-parts, both audited); it stands aside for good, at its
 * first use, when ProjectileUtil or RayTraceUtil carries a merged mixin outside the tested set (Immersive Aircraft's and
 * KnightLib's hooks on the same trace add their own searches and hits), and for a reach above 256 blocks.
 *
 * Kind change, off by default (the speed gate): the answers are exact, but every mob the look ray does cross (the one under
 * the crosshair, and any standing in line behind it or crammed into one spot within reach) is traced about 5 percent
 * slower than shipped, a cost that repeats per mob; mobs off the ray are answered about 30 times faster.
 *
 * Flag: -Dbons_and_furious.l2hostilityNamePlateHoverDirect=false runs the shipped trace (the config key decides first).
 */
public final class NamePlateSight {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.l2hostilityNamePlateHoverDirect", "true"));
    private static final String MERGED = "org.spongepowered.asm.mixin.transformer.meta.MixinMerged";
    /** Mixins merged into ProjectileUtil that change only other methods (checked on their jars): Create, Valkyrien Skies
     *  (both on getEntityHitResult(Entity, Vec3, Vec3, AABB, Predicate, double)) and Vivecraft (getHitResultOnViewVector). */
    public static final Set<String> TESTED_PROJECTILE_UTIL_MIXINS = Set.of(
            "com.simibubi.create.foundation.mixin.ProjectileUtilMixin",
            "org.valkyrienskies.mod.mixin.world.entity.projectile.ProjectileUtilMixin",
            "org.vivecraft.mixin.world.entity.projectile.ProjectileUtilMixin");
    /** The inflate of ProjectileUtil.getEntityHitResult(Level, Entity, Vec3, Vec3, AABB, Predicate), plus our margin. */
    private static final double GROW = (double) 0.3F + 1.0E-4;
    private static final double MAX_REACH = 256.0;
    /** Traces answered without the search (for probes and the harness). */
    public static long direct;
    private static volatile int census;   // 0 unknown, 1 tested set, -1 stands aside
    private static volatile boolean announced;

    private NamePlateSight() {
    }

    /** True when every mixin merged into ProjectileUtil and RayTraceUtil belongs to the tested set (checked once). */
    public static boolean censusOk() {
        int c = census;
        if (c != 0) return c > 0;
        Set<String> unknown = new TreeSet<>();
        try {
            collect(Class.forName("net.minecraft.world.entity.projectile.ProjectileUtil", false, NamePlateSight.class.getClassLoader()),
                    TESTED_PROJECTILE_UTIL_MIXINS, unknown);
            collect(RayTraceUtil.class, Set.of(), unknown);
        } catch (Throwable t) {
            unknown.add("(could not list them: " + t + ")");
        }
        if (unknown.isEmpty()) {
            census = 1;
            return true;
        }
        census = -1;
        LOGGER.info("Bons and Furious: l2hostility_name_plate_hover_direct stands aside (other mods change the look trace: {})", unknown);
        return false;
    }

    private static void collect(Class<?> c, Set<String> tested, Set<String> unknown) throws Exception {
        for (Method m : c.getDeclaredMethods()) {
            for (Annotation a : m.getDeclaredAnnotations()) {
                if (!a.annotationType().getName().equals(MERGED)) continue;
                String mixin = String.valueOf(a.annotationType().getMethod("mixin").invoke(a));
                if (!tested.contains(mixin)) unknown.add(mixin);
            }
        }
    }

    /**
     * The answer of the shipped RayTraceUtil.rayTraceEntity(player, reach, pred) for this render event's mob (pred admits
     * only that mob). Null without the search when the look ray stays clear of the mob's grown box (the profiler counter is
     * incremented as the search would); otherwise RayTraceUtil.rayTraceEntity's own call sequence (L2 Library 2.5.5,
     * LGPL-2.1: the box AABB(eye, end).inflate(1) and ProjectileUtil.getEntityHitResult) with the eye position and ray end
     * already computed here. The shipped call itself, at once, for the mob under the game's own crosshair this frame
     * (Minecraft.crosshairPickEntity: almost always hovered, so the clear-ray test would be wasted; only a hint, the
     * answer is the shipped one either way), and when the switch is off or stands aside.
     */
    public static EntityHitResult trace(Player player, double reach, Predicate<Entity> pred, Entity target) {
        Minecraft mc = Minecraft.m_91087_();
        if (!enabled || mc != null && mc.f_91076_ == target || !censusOk() || !(target instanceof LivingEntity) || !(reach <= MAX_REACH)
                || !pred.test(target))
            return RayTraceUtil.rayTraceEntity(player, reach, pred);
        Vec3 pos = new Vec3(player.m_20185_(), player.m_20188_(), player.m_20189_());
        Vec3 end = RayTraceUtil.getRayTerm(pos, player.m_146909_(), player.m_146908_(), reach);
        if (!clear(pos.f_82479_, pos.f_82480_, pos.f_82481_, end.f_82479_, end.f_82480_, end.f_82481_, target.m_20191_()))
            return ProjectileUtil.m_37304_(player.m_9236_(), player, pos, end, new AABB(pos, end).m_82400_(1.0), pred);
        player.m_9236_().m_46473_().m_6174_("getEntities");
        direct++;
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: l2hostility_name_plate_hover_direct applies (L2 Hostility's name plates test the hovered mob directly)");
        }
        return null;
    }

    /**
     * True when the segment (x0, y0, z0) - (x1, y1, z1) does not meet the box b grown by GROW: one of the six separating axes
     * of a segment and a box (the three box axes, and the segment direction crossed with each) separates them. In exact
     * arithmetic this is the exact segment / box intersection test; the rounding of these few products (relative 1e-16, the
     * coordinates' own rounding 4e-9 at 3e7) is far below the 1e-4 margin, so a true answer is certain.
     */
    static boolean clear(double x0, double y0, double z0, double x1, double y1, double z1, AABB b) {
        double hx = (b.f_82291_ - b.f_82288_) * 0.5 + GROW, hy = (b.f_82292_ - b.f_82289_) * 0.5 + GROW, hz = (b.f_82293_ - b.f_82290_) * 0.5 + GROW;
        double wx = (x1 - x0) * 0.5, wy = (y1 - y0) * 0.5, wz = (z1 - z0) * 0.5;
        double vx = x0 + wx - (b.f_82288_ + b.f_82291_) * 0.5, vy = y0 + wy - (b.f_82289_ + b.f_82292_) * 0.5, vz = z0 + wz - (b.f_82290_ + b.f_82293_) * 0.5;
        double ax = Math.abs(wx), ay = Math.abs(wy), az = Math.abs(wz);
        return Math.abs(vx) > hx + ax || Math.abs(vy) > hy + ay || Math.abs(vz) > hz + az
                || Math.abs(vy * wz - vz * wy) > hy * az + hz * ay
                || Math.abs(vz * wx - vx * wz) > hx * az + hz * ax
                || Math.abs(vx * wy - vy * wx) > hx * ay + hy * ax;
    }
}
