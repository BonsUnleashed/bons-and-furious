package bons.furious.patch.fallentraits_coo49;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.kayn.fallen_traits.client.SizeRenderContext;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious trade-off switch fallentraits_render_scale_frame_memo (Fallen Traits & Relics 1.0.0, LGPL-2.1-or-later;
 * client side; kind "change", ships off). Ours. Idea: Collections Of Optimizations (fallentraits.cacheRenderScale), idea
 * text only.
 *
 * SizeTrait.getScale(entity) is asked by Fallen Traits' renderer hooks (LivingEntityRenderer.render, GeoEntityRenderer
 * actuallyRender, its Cataclysm renderer hooks) for every rendered living entity, one or more times per frame: two
 * MobTraitCap resolves, up to two ForgeData reads and a SIZE_SCALE attribute lookup each. Its inputs only change in ticks
 * and in packet handling.
 *
 * While the client renders the world (LevelRenderer.renderLevel, marked on the thread that runs it; the mark is cleared at
 * its end and at the start of every client tick), scale() keeps each living entity's scale with its tickCount and answers
 * the kept value until the entity's next tick. Everything else asks getScale live: entity dimensions in ticks (hitboxes,
 * eye height), other threads, and the inventory preview (which getScale answers with 1 before anything else). What
 * differs: a size change shows on screen up to one tick late.
 *
 * Flag: -Dbons_and_furious.fallentraitsRenderScaleFrameMemo=false.
 */
public final class FtScale {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.fallentraitsRenderScaleFrameMemo", "true"));
    private static volatile Thread rendering;
    private static volatile boolean announced;

    private FtScale() {
    }

    /** LevelRenderer.renderLevel HEAD: this thread is rendering the world. */
    public static void worldRenderStart() {
        rendering = Thread.currentThread();
    }

    /** LevelRenderer.renderLevel RETURN and Minecraft.tick HEAD: not rendering the world. */
    public static void worldRenderEnd() {
        rendering = null;
    }

    /** True while the calling thread renders the world (harness). */
    public static boolean inWorldRender() {
        return rendering == Thread.currentThread();
    }

    /** The wrapped SizeTrait.getScale(entity). */
    public static float scale(Entity entity, Operation<Float> original) {
        if (!enabled || rendering != Thread.currentThread() || !(entity instanceof LivingEntity living) || !(entity instanceof FtScaleMemo memo)
                || SizeRenderContext.isInventoryPreview()) {
            return original.call(entity);
        }
        int tick = living.f_19797_ + 1;
        if (memo.bons$fallentraitsCoo49ScaleTick() == tick) return memo.bons$fallentraitsCoo49Scale();
        float s = original.call(entity);
        memo.bons$fallentraitsCoo49KeepScale(tick, s);
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: fallentraits_render_scale_frame_memo applies (Fallen Traits sizes are taken once per tick while the world renders)");
        }
        return s;
    }
}
