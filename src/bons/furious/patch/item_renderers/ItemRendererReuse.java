package bons.furious.patch.item_renderers;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.logging.LogUtils;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.server.packs.resources.ReloadInstance;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.slf4j.Logger;

/**
 * Bons and Furious switches *_item_renderer_reuse and item_renderer_reload_tracker (client; Minecraft 1.20.1 on Forge
 * 47.4.16; SRG names). Group item_renderers, since 1.0.36.
 *
 * What the mods do. Forge draws an item with a custom renderer as
 * IClientItemExtensions.of(stack).getCustomRenderer().renderByItem(...), every frame, for every such item on screen
 * (hand, hotbar, inventory, JEI, item frames, dropped items). The sites below build a NEW renderer inside
 * getCustomRenderer, so every draw constructs one: vanilla BlockEntityWithoutLevelRenderer's field initialisers create
 * 7 dummy block entities (chest, trapped chest, ender chest, banner, bed, conduit, decorated pot), and Forge's
 * BlockEntity constructor posts an AttachCapabilitiesEvent for each (about 20 listeners in this pack); then the mod's
 * own constructor runs (Cataclysm also bakes its skull models).
 *
 * What the switches do. On the render thread, while no client resource reload is in flight, a site hands out one
 * renderer per extensions object (one per item): the first call builds it through the original code, later calls
 * return it. Proven per renderer class (notes/item_renderers.md): a renderer of that class carries nothing from one
 * draw into the next, so the reused one is in the state a new one would be in:
 *   - no instance fields of their own: Aquaculture AquaItemRenderer, Nether Expansion JNEItemRenderer, Rats RatsBEWLR,
 *     TACZ Ammo/Attachment/GunSmithTable item renderers, XercaPaint CanvasItemRenderer; the vanilla dummies they may
 *     reach through super.renderByItem are overwritten from the stack before each use (banner fromItem, bed setColor,
 *     decorated pot setFromItem) and only read otherwise;
 *   - Cataclysm CMItemstackRenderer: skullModels, baked from the EntityModelSet in the constructor and only read
 *     afterwards (its onResourceManagerReload is never called on these instances); the memo is dropped at every reload,
 *     so a reused one always holds what a new one would bake. Its constructor also refills a static texture-name array
 *     with equal ResourceLocations; the reused renderer leaves the previous, equal ones in place;
 *   - Alex's Caves ACItemstackRenderer: renderedDreadbowArrow (null in a new one) is set back to null before each
 *     hand-out (ReuseReset), so the dread bow still creates its arrow per draw as before;
 *   - Alex's Mobs AMItemstackRenderer: renderedEntites and blockedRenderEntities (empty in a new one) are cleared before
 *     each hand-out, so entity items still create their entity per draw and retry failed types as before.
 * Any other thread, an in-flight reload (start to done(), counted by item_renderer_reload_tracker), the tracker not
 * yet having seen the start-up reload, a renderer of another class (another mod's wrapper) or the switch off: the
 * original call, a new renderer as before. The memo is dropped when a reload starts and when it ends, so the next call
 * builds a new one from the reloaded state.
 *
 * The one documented difference (exact variant): per draw, the 7 dummy block entities (and the mod's own dummies) are
 * not constructed again, so AttachCapabilitiesEvent listeners see those dummies once per item instead of once per draw;
 * nothing else references the dummies.
 *
 * -Dbons_and_furious.<camelName>=false switches a site off at run time (camelName per switch, e.g.
 * alexscavesItemRendererReuse); -Dbons_and_furious.itemRendererReloadTracker=false turns every site off.
 * -Dbons_and_furious.<camelName>.shadow=true (verification runs only): at each reuse a new renderer is also built by the
 * original call and its fields are compared with the reused one's (Site.shadowChecks / shadowMismatches, WARN for the
 * first 20); the reused one is handed out.
 */
public final class ItemRendererReuse {
    private static final Logger LOGGER = LogUtils.getLogger();

    /** One per switch: its run-time flag, shadow flag and counters, and the renderer class(es) its proof covers. */
    public static final class Site {
        public final String key;
        public volatile boolean enabled;
        public final boolean shadow;
        public final AtomicLong builds = new AtomicLong(), shadowChecks = new AtomicLong(), shadowMismatches = new AtomicLong();
        private final String[] renderers;
        private volatile boolean announced, warned;

        Site(String key, String camel, String... renderers) {
            this.key = key;
            this.enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious." + camel, "true"));
            this.shadow = Boolean.getBoolean("bons_and_furious." + camel + ".shadow");
            this.renderers = renderers;
        }

        boolean proven(Class<?> c) {
            String n = c.getName();
            for (String r : renderers) if (r.equals(n)) return true;
            return false;
        }
    }

    public static final Site AQUACULTURE = new Site("aquaculture_item_renderer_reuse", "aquacultureItemRendererReuse",
            "com.teammetallurgy.aquaculture.client.renderer.AquaItemRenderer");
    public static final Site NETHEREXP = new Site("netherexp_item_renderer_reuse", "netherexpItemRendererReuse",
            "net.jadenxgamer.netherexp.registry.item.JNEItemRenderer");
    public static final Site CATACLYSM = new Site("cataclysm_item_renderer_reuse", "cataclysmItemRendererReuse",
            "com.github.L_Ender.cataclysm.client.render.CMItemstackRenderer");
    public static final Site RATS = new Site("rats_item_renderer_reuse", "ratsItemRendererReuse",
            "com.github.alexthe666.rats.client.render.RatsBEWLR");
    public static final Site ALEXSCAVES = new Site("alexscaves_item_renderer_reuse", "alexscavesItemRendererReuse",
            "com.github.alexmodguy.alexscaves.client.render.item.ACItemstackRenderer");
    public static final Site ALEXSMOBS = new Site("alexsmobs_item_renderer_reuse", "alexsmobsItemRendererReuse",
            "com.github.alexthe666.alexsmobs.client.render.AMItemstackRenderer");
    public static final Site TACZ = new Site("tacz_item_renderer_reuse", "taczItemRendererReuse",
            "com.tacz.guns.client.renderer.item.AmmoItemRenderer", "com.tacz.guns.client.renderer.item.AttachmentItemRenderer",
            "com.tacz.guns.client.renderer.item.GunSmithTableItemRenderer");
    public static final Site XERCAPAINT = new Site("xercapaint_item_renderer_reuse", "xercapaintItemRendererReuse",
            "xerca.xercapaint.client.CanvasItemRenderer");

    /** item_renderer_reload_tracker's run-time flag: off = every site runs the original. */
    public static volatile boolean trackerEnabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.itemRendererReloadTracker", "true"));
    private static final AtomicInteger GENERATION = new AtomicInteger(), IN_FLIGHT = new AtomicInteger();
    private static volatile boolean trackerSeen, trackerBroken;

    /** The memo of one extensions object (one item): kept in a field the site mixin adds. Render thread only. */
    public static final class Slot {
        BlockEntityWithoutLevelRenderer renderer;
        int generation;
    }

    /** Implemented by the site mixins (one Slot per extensions object, created on first use). */
    public interface SlotHolder {
        Slot bons$itemRendererSlot();
    }

    private ItemRendererReuse() {
    }

    /** ReloadableResourceManager.createReload returned this reload: in flight until its done() future completes. */
    public static void reloadStarted(ReloadInstance reload) {
        CompletableFuture<?> done = null;
        try {
            if (reload != null) done = reload.m_7237_();
        } catch (Throwable t) {
            done = null;
        }
        if (done == null) {
            if (!trackerBroken) LOGGER.warn("Bons and Furious: item_renderer_reload_tracker could not follow a resource reload; item renderer reuse stays off");
            trackerBroken = true;
            GENERATION.incrementAndGet();
            return;
        }
        IN_FLIGHT.incrementAndGet();
        GENERATION.incrementAndGet();
        trackerSeen = true;
        done.whenComplete((r, t) -> {
            GENERATION.incrementAndGet();
            IN_FLIGHT.decrementAndGet();
        });
    }

    static boolean onRenderThread() {
        Minecraft mc = Minecraft.m_91087_();
        return mc != null && mc.m_18695_();
    }

    /** A site's getCustomRenderer(): the memo of this extensions object, or the original call. */
    public static BlockEntityWithoutLevelRenderer reuse(Object holder, Site site, Operation<BlockEntityWithoutLevelRenderer> original) {
        if (!site.enabled || !trackerEnabled || !trackerSeen || trackerBroken || IN_FLIGHT.get() != 0 || !onRenderThread())
            return original.call();
        Slot s = ((SlotHolder) holder).bons$itemRendererSlot();
        int gen = GENERATION.get();
        BlockEntityWithoutLevelRenderer r = s.renderer;
        if (r != null && s.generation == gen) {
            if (r instanceof ReuseReset z) z.bons$resetForReuse();
            if (site.shadow) shadowCheck(site, r, original);
            return r;
        }
        BlockEntityWithoutLevelRenderer fresh = original.call();
        if (fresh != null && site.proven(fresh.getClass())) {
            s.renderer = fresh;
            s.generation = gen;
            site.builds.incrementAndGet();
            if (!site.announced) {
                site.announced = true;
                LOGGER.info("Bons and Furious: {} applies (one {} per item instead of a new one with 7 dummy block entities for every draw){}",
                        site.key, fresh.getClass().getSimpleName(), site.shadow ? " - shadow verification on" : "");
            }
        } else {
            s.renderer = null;
            if (fresh != null && !site.warned) {
                site.warned = true;
                LOGGER.warn("Bons and Furious: {} steps aside for {}: getCustomRenderer returned a {}, not a renderer this switch was proven on",
                        site.key, holder.getClass().getName(), fresh.getClass().getName());
            }
        }
        return fresh;
    }

    // ------------------------------------------------------------------ shadow verification

    private static void shadowCheck(Site site, BlockEntityWithoutLevelRenderer reused, Operation<BlockEntityWithoutLevelRenderer> original) {
        BlockEntityWithoutLevelRenderer fresh = original.call();
        String diff = stateDifference(reused, fresh);
        site.shadowChecks.incrementAndGet();
        if (diff != null) {
            long m = site.shadowMismatches.incrementAndGet();
            if (m <= 20) LOGGER.warn("Bons and Furious: {} shadow mismatch #{}: the reused {} differs from a new one in {}", site.key, m,
                    reused.getClass().getSimpleName(), diff);
        }
    }

    private static final Map<Class<?>, List<Field>> FIELDS = new ConcurrentHashMap<>();

    private static List<Field> fields(Class<?> c) {
        return FIELDS.computeIfAbsent(c, k -> {
            List<Field> out = new ArrayList<>();
            for (Class<?> x = k; x != null && x != Object.class; x = x.getSuperclass()) {
                for (Field f : x.getDeclaredFields()) {
                    if (Modifier.isStatic(f.getModifiers())) continue;
                    f.setAccessible(true);
                    out.add(f);
                }
            }
            return out;
        });
    }

    /**
     * Null when the reused renderer's instance state equals a new one's: same class; per field: equal primitives, both
     * null, the same object (dispatcher, model set, captured outer objects), equal-size collections and maps with equal
     * keys and value classes (skull models, the cleared caches), and for block entities the same class (vanilla's dummies
     * are overwritten before each use). Otherwise the first differing field.
     */
    public static String stateDifference(Object reused, Object fresh) {
        if (reused == null || fresh == null || reused.getClass() != fresh.getClass()) return "class";
        try {
            for (Field f : fields(reused.getClass())) {
                Object a = f.get(reused), b = f.get(fresh);
                if (f.getType().isPrimitive()) {
                    if (!a.equals(b)) return f.getName();
                } else if (a == null || b == null) {
                    if (a != b) return f.getName();
                } else if (a == b) {
                    continue;
                } else if (a instanceof BlockEntity) {
                    if (a.getClass() != b.getClass()) return f.getName();
                } else if (a instanceof Map<?, ?> ma && b instanceof Map<?, ?> mb) {
                    if (!ma.keySet().equals(mb.keySet())) return f.getName();
                    for (Object k : ma.keySet()) {
                        Object va = ma.get(k), vb = mb.get(k);
                        if ((va == null) != (vb == null) || va != null && va.getClass() != vb.getClass()) return f.getName() + "[" + k + "]";
                    }
                } else if (a instanceof Collection<?> ca && b instanceof Collection<?> cb) {
                    if (ca.size() != cb.size()) return f.getName();
                } else {
                    return f.getName();
                }
            }
            return null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return "unreadable (" + e + ")";
        }
    }
}
