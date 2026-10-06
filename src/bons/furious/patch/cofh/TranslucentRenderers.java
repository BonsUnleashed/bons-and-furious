package bons.furious.patch.cofh;

import bons.furious.guard.Guards;
import bons.furious.mixin.cofh.DispatcherRenderersAccessor;
import cofh.lib.client.renderer.entity.ITranslucentRenderer;
import com.google.common.collect.ImmutableMap;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch cofh_translucent_renderer_memo (CoFH Core 11.0.2; client).
 *
 * After the particles of every frame CoFH Core walks every client entity and asks the EntityRenderDispatcher for its
 * renderer, only to keep the few whose renderer is one of CoFH's translucent ones (ITranslucentRenderer, e.g. the
 * electric field). That is one renderer lookup per entity per frame: a map lookup by EntityType, or for a player its
 * skin model name.
 *
 * The lookup's answer only matters as "is it a translucent renderer". The vanilla getRenderer returns the renderer the
 * dispatcher's maps hold for the entity's EntityType (players: their model name, else "default"), and both maps are
 * immutable and replaced as a whole on every resource reload. So the EntityTypes whose renderer is translucent, and
 * whether any player renderer is, are worked out once per pair of maps; an entity of any other type (a player when no
 * player renderer is translucent) gets null, which CoFH treats exactly like a renderer that is not translucent. Every
 * other entity still gets dispatcher.getRenderer itself.
 *
 * That is only true while getRenderer is the vanilla method. Mods do change it (BadOptimizations overwrites it with a
 * per-type cache, Friends and Foes and Vivecraft return their own renderers for some entities), so the switch checks,
 * after every other mod's mixins have been applied to the dispatcher (Guards.untouched, see DispatcherRenderersAccessor),
 * that getRenderer still has the vanilla fingerprint; when it does not, or the check could not run, or a map is not an
 * ImmutableMap, every entity gets getRenderer as before.
 *
 * -Dbons_and_furious.cofhTranslucentRenderers=false asks getRenderer every time; -Dbons_and_furious.cofhTranslucentRenderers.shadow=true
 * (verification runs only) asks getRenderer for every entity, also where getRenderer is changed by another mod, hands
 * CoFH its answer and counts the entities the fast path would have skipped although their renderer is translucent
 * (SHADOW_CHECKS / SHADOW_MISMATCHES).
 */
public final class TranslucentRenderers {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static final String KEY = "cofh_translucent_renderer_memo";
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.cofhTranslucentRenderers", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.cofhTranslucentRenderers.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    private static final EntityType<?>[] NONE = new EntityType<?>[0];
    private static int state;                     // 0 = not decided, 1 = fast path, 2 = getRenderer for everything
    /**
     * The maps the table below was built from. 1.0.34: weakly held. A resource reload replaces both maps, and the strong
     * references kept the replaced ones (every old EntityRenderer with its models) reachable from here until the next
     * translucent pass in a world. While the dispatcher holds a map, its reference cannot be cleared, so the identity
     * checks decide as before; a cleared reference reads null, which no dispatcher map is (they start as ImmutableMap.of()
     * and are replaced by built maps), so it counts as "not the current map", as the replaced map did.
     */
    private static WeakReference<Map<?, ?>> typeMap = new WeakReference<>(null), playerMap = new WeakReference<>(null);
    private static EntityType<?>[] translucentTypes = NONE;
    private static boolean translucentPlayer;

    private TranslucentRenderers() {
    }

    /** dispatcher.m_114382_(entity) as CoFH's ITranslucentRenderer.renderTranslucent asks it; render thread only. */
    public static EntityRenderer<?> renderer(EntityRenderDispatcher dispatcher, Entity entity) {
        if (!enabled) return dispatcher.m_114382_(entity);
        if (state != 1 && !SHADOW && (state == 2 || !decide())) return dispatcher.m_114382_(entity);
        Map<?, ?> types = dispatcher.f_114362_;
        Map<?, ?> players = ((DispatcherRenderersAccessor) dispatcher).bons$playerRenderers();
        if (types != typeMap.get() || players != playerMap.get()) {   // 1.0.34: weak references (see the fields)
            if (!(types instanceof ImmutableMap) || !(players instanceof ImmutableMap)) return dispatcher.m_114382_(entity);
            rebuild(types, players);
        }
        boolean translucent;
        if (entity instanceof AbstractClientPlayer) {
            translucent = translucentPlayer;
        } else {
            translucent = false;
            EntityType<?> type = entity.m_6095_();
            for (EntityType<?> t : translucentTypes) {
                if (t == type) {
                    translucent = true;
                    break;
                }
            }
        }
        if (translucent) return dispatcher.m_114382_(entity);
        if (SHADOW) {
            EntityRenderer<?> real = dispatcher.m_114382_(entity);
            SHADOW_CHECKS.incrementAndGet();
            if (real instanceof ITranslucentRenderer && SHADOW_MISMATCHES.incrementAndGet() <= 20) {
                LOGGER.warn("Bons and Furious: cofh_translucent_renderer_memo shadow mismatch: {} ({}) has the translucent renderer {}",
                        entity, entity.m_6095_(), real.getClass().getName());
            }
            return real;
        }
        return null;
    }

    /** First call: the fast path runs only when getRenderer was left as the vanilla method by every other mod. */
    private static boolean decide() {
        String why = Guards.untouched(KEY);
        if ("".equals(why)) {
            state = 1;
            LOGGER.info("Bons and Furious: cofh_translucent_renderer_memo applies (CoFH's translucent pass looks renderers up only for translucent entity types)");
            return true;
        }
        state = 2;
        LOGGER.info("Bons and Furious: cofh_translucent_renderer_memo steps aside at run time: EntityRenderDispatcher.getRenderer {}; CoFH's translucent pass is left as it is",
                why == null ? "could not be checked" : why);
        return false;
    }

    private static void rebuild(Map<?, ?> types, Map<?, ?> players) {
        List<EntityType<?>> found = new ArrayList<>();
        for (Map.Entry<?, ?> e : types.entrySet()) {
            if (e.getValue() instanceof ITranslucentRenderer && e.getKey() instanceof EntityType<?> t) found.add(t);
        }
        boolean player = false;
        for (Object r : players.values()) player |= r instanceof ITranslucentRenderer;
        translucentTypes = found.toArray(NONE);
        translucentPlayer = player;
        typeMap = new WeakReference<>(types);     // 1.0.34: weakly held (see the fields)
        playerMap = new WeakReference<>(players);
        LOGGER.debug("Bons and Furious: cofh_translucent_renderer_memo: {} translucent entity types {}, translucent player renderer: {}",
                found.size(), found, player);
    }
}
