package bons.furious.patch.mob_refresh_c49;

import java.util.concurrent.atomic.LongAdder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.entity.EntityEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch mob_tick_dimension_refresh_always_skip (a trade-off, off by default; Minecraft 1.20.1 / Forge
 * 47.4.28, both sides). Mojang and Forge code is no-copy: injectors only; no Born in Chaos or Critters n' Crawlers code is
 * carried (their mobs are matched by package name). Ours, idea from Collections Of Optimizations
 * (borninchaos.skipRedundantDimensionRefresh, cnc.skipRedundantDimensionRefresh), idea text only.
 *
 * What the game does: Entity.refreshDimensions (Born in Chaos' 83 and Critters n' Crawlers' 28 mobs call it at the end of
 * every baseTick) computes the size for the current pose and the eye height for it, posts Forge's EntityEvent.Size, stores
 * the event's size and eye height, re-applies the position (setPos: the same position and a new bounding box from the size)
 * and searches a free position only when the size grew.
 *
 * What the switch does, for Born in Chaos and Critters n' Crawlers mobs (never players), inside refreshDimensions: when the
 * new size equals the current one bit for bit (width, height, fixed), the eye height for it equals the current eye height,
 * the bounding box already equals the size's box at the current position bit for bit, and the mob's class keeps Entity's own
 * setPos, makeBoundingBox, reapplyPosition and position, the refresh would store equal values: the Size event is then built
 * exactly as Forge builds it but not posted, and the position re-apply is left out (the mob keeps its equal bounding box
 * object). Trade-off: a mod that changes those mobs' size or eye height through the Size event no longer sees these
 * unchanged refreshes.
 *
 * Flag: -Dbons_and_furious.mobTickDimensionRefreshAlwaysSkip=false restores the refresh at runtime.
 */
public final class RefreshSkip {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean alwaysEnabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.mobTickDimensionRefreshAlwaysSkip", "true"));
    /** Skips made (probes and the harness). */
    public static final LongAdder SKIPPED_ALWAYS = new LongAdder();
    private static volatile boolean announcedAlways;

    /**
     * Classes whose position re-apply is Entity's own (setPos, makeBoundingBox and reapplyPosition not overridden) and whose
     * position() is Entity's field getter (the box check reads it).
     */
    private static final ClassValue<Boolean> PLAIN = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> c) {
            try {
                return declaredBy(c, "m_6034_", double.class, double.class, double.class) == Entity.class
                        && declaredBy(c, "m_142242_") == Entity.class && declaredBy(c, "m_20090_") == Entity.class
                        && declaredBy(c, "m_20182_") == Entity.class;
            } catch (Throwable t) {
                return false;
            }
        }
    };

    /** The switch's scope: the two mods whose mobs refresh their size on every tick. */
    private static final ClassValue<Boolean> PER_TICK_MOD = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> c) {
            String n = c.getName();
            return n.startsWith("net.mcreator.borninchaosv.") || n.startsWith("net.imasillylittleguy.cnc.");
        }
    };

    private RefreshSkip() {
    }

    private static Class<?> declaredBy(Class<?> c, String name, Class<?>... params) {
        for (Class<?> k = c; k != null; k = k.getSuperclass()) {
            try {
                k.getDeclaredMethod(name, params);
                return k;
            } catch (NoSuchMethodException e) {
                // not declared here: the superclass
            }
        }
        return null;
    }

    /** True when the refresh would store values equal to the current ones and re-apply the same position and box. */
    static boolean unchanged(Entity e, EntityDimensions old, EntityDimensions now, float eye) {
        if (old == null || now == null) return false;
        if (Float.floatToRawIntBits(old.f_20377_) != Float.floatToRawIntBits(now.f_20377_) || Float.floatToRawIntBits(old.f_20378_) != Float.floatToRawIntBits(now.f_20378_)
                || old.f_20379_ != now.f_20379_) return false;
        if (Float.floatToRawIntBits(eye) != Float.floatToRawIntBits(e.m_20192_())) return false;
        if (!PLAIN.get(e.getClass())) return false;
        AABB box = e.m_20191_(), want = now.m_20393_(e.m_20182_());
        return box != null && bits(box.f_82288_, want.f_82288_) && bits(box.f_82289_, want.f_82289_) && bits(box.f_82290_, want.f_82290_)
                && bits(box.f_82291_, want.f_82291_) && bits(box.f_82292_, want.f_82292_) && bits(box.f_82293_, want.f_82293_);
    }

    private static boolean bits(double a, double b) {
        return Double.doubleToRawLongBits(a) == Double.doubleToRawLongBits(b);
    }

    /** The switch's decision: Born in Chaos and Critters n' Crawlers mobs, whatever the Size listeners. */
    public static boolean alwaysSkip(Entity e, EntityDimensions old, EntityDimensions now, float eye) {
        if (!alwaysEnabled || e instanceof Player || !PER_TICK_MOD.get(e.getClass()) || !unchanged(e, old, now, eye)) return false;
        SKIPPED_ALWAYS.increment();
        if (!announcedAlways) {
            announcedAlways = true;
            LOGGER.info("Bons and Furious: mob_tick_dimension_refresh_always_skip applies (Born in Chaos and Critters n' Crawlers mobs skip their unchanged per-tick size refresh)");
        }
        return true;
    }

    /** The event Forge would have built (ForgeEventFactory.getEntitySizeForge), not posted. */
    public static EntityEvent.Size unposted(Entity e, Pose pose, EntityDimensions old, EntityDimensions now, float eye) {
        return new EntityEvent.Size(e, pose, old, now, e.m_20192_(), eye);
    }
}
