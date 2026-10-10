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
 * Bons and Furious switches *_item_renderer_reuse and item_renderer_reload_tracker (client; Minecraft 1.21.1 on NeoForge
 * 21.1, Mojang names). Group item_renderers, since 1.0.36. Tested builds: Aquaculture 1.21.1-2.7.21, Jaden's Nether
 * Expansion 2.4.1 (NeoForge 1.21.1).
 *
 * What the mods do. NeoForge draws an item with a custom renderer as
 * IClientItemExtensions.of(stack).getCustomRenderer().renderByItem(...) (ItemRenderer.render, BlockRenderDispatcher's
 * single-block path), every frame, for every such item on screen (hand, hotbar, inventory, JEI, item frames, dropped
 * items). The sites below build a NEW renderer inside getCustomRenderer, so every draw constructs one: vanilla
 * BlockEntityWithoutLevelRenderer's field initialisers create 7 dummy block entities (chest, trapped chest, ender chest,
 * banner, bed, conduit, decorated pot), then the mod's own constructor runs.
 *
 * What the switches do. On the render thread, while no client resource reload is in flight, a site hands out one
 * renderer per extensions object: the first call builds it through the original code, later calls return it. Proven per
 * renderer class (notes/item_renderers.md): a renderer of that class carries nothing from one draw into the next, so the
 * reused one is in the state a new one would be in. Neither class below has instance fields of its own:
 *   - Aquaculture AquaItemRenderer (the extensions of each Aquaculture block item with a 3D item model, built per item in
 *     BlockItemWithoutLevelRenderer.initializeClient): renderByItem builds its own tackle box / Neptune's bounty block
 *     entity per draw and never touches the inherited dummies;
 *   - Nether Expansion JNEItemRenderer (one extensions object, JNEItemExtensions.itemExt, registered for the Will-o'-Wisp,
 *     the Shotgun Fist and the Pump-Charge Shotgun): only static models (baked at class init, posed per draw either way);
 *     it calls super.renderByItem first, whose vanilla dummies are overwritten from the stack before each use (banner
 *     fromItem, bed setColor, decorated pot setFromItem: every rendered field) and only read otherwise; the skull, shield
 *     and trident models stay null in a renderer whose onResourceManagerReload is never called, reused or new.
 * Any other thread, an in-flight reload (start to done(), counted by item_renderer_reload_tracker), the tracker not
 * yet having seen the start-up reload, a renderer of another class (another mod's wrapper) or the switch off: the
 * original call, a new renderer as before. The memo is dropped when a reload starts and when it ends, so the next call
 * builds a new one from the reloaded state.
 *
 * Nothing else observes the constructions left out: NeoForge 21's BlockEntity constructor posts no event (the 1.20.1
 * build's one documented difference, Forge's AttachCapabilitiesEvent seeing the dummies once per item instead of once per
 * draw, has no counterpart here) and nothing references the dummies outside the renderer.
 *
 * -Dbons_and_furious.<camelName>=false switches a site off at run time (aquacultureItemRendererReuse,
 * netherexpItemRendererReuse); -Dbons_and_furious.itemRendererReloadTracker=false turns every site off.
 * -Dbons_and_furious.<camelName>.shadow=true (verification runs only): at each reuse a new renderer is also built by the
 * original call and its fields are compared with the reused one's (Site.shadowChecks / shadowMismatches, also summed in
 * the class-level SHADOW_CHECKS / SHADOW_MISMATCHES the port probe reads; WARN for the first 20); the reused one is
 * handed out.
 *
 * Ported to 1.21.1: Mojang names; Nether Expansion 2.4.1 moved its site from three per-item anonymous extensions
 * (JackhammerFistItem$1, PumpChargeShotgunItem$1, ShotgunFistItem$1) to one shared JNEItemExtensions$1 and its renderer
 * to net.jadenxgamer.netherexp.client.JNEItemRenderer; Aquaculture 2.7.21's site and renderer are the same code. The
 * Cataclysm and XercaPaint sites are retired (Cataclysm 3.33 and XercaPaint 2.0.1 already hand out one shared renderer),
 * the Rats, Alex's Caves, Alex's Mobs and TACZ sites have no author-published NeoForge 1.21.1 target; the reset hook
 * only the Alex's Caves and Alex's Mobs renderers needed (ReuseReset) is therefore not carried.
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
            "net.jadenxgamer.netherexp.client.JNEItemRenderer");

    /**
     * Class-level view for the port probe's shadow harvest (probe-shadow-helpers.txt reads SHADOW / SHADOW_CHECKS /
     * SHADOW_MISMATCHES of a class): any site's shadow flag, and every site's checks and mismatches summed. The
     * per-site counters above stay as they are (the Forge rig's "ItemRendererReuse#SITE" lines read those).
     */
    public static final boolean SHADOW = AQUACULTURE.shadow || NETHEREXP.shadow;
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();

    /** item_renderer_reload_tracker's run-time flag: off = every site runs the original. */
    public static volatile boolean trackerEnabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.itemRendererReloadTracker", "true"));
    private static final AtomicInteger GENERATION = new AtomicInteger(), IN_FLIGHT = new AtomicInteger();
    private static volatile boolean trackerSeen, trackerBroken;

    /** The memo of one extensions object: kept in a field the site mixin adds. Render thread only. */
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
            if (reload != null) done = reload.done();
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
        Minecraft mc = Minecraft.getInstance();
        return mc != null && mc.isSameThread();
    }

    /** A site's getCustomRenderer(): the memo of this extensions object, or the original call. */
    public static BlockEntityWithoutLevelRenderer reuse(Object holder, Site site, Operation<BlockEntityWithoutLevelRenderer> original) {
        if (!site.enabled || !trackerEnabled || !trackerSeen || trackerBroken || IN_FLIGHT.get() != 0 || !onRenderThread())
            return original.call();
        Slot s = ((SlotHolder) holder).bons$itemRendererSlot();
        int gen = GENERATION.get();
        BlockEntityWithoutLevelRenderer r = s.renderer;
        if (r != null && s.generation == gen) {
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
        SHADOW_CHECKS.incrementAndGet();
        if (diff != null) {
            SHADOW_MISMATCHES.incrementAndGet();
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
     * null, the same object (dispatcher, model set), equal-size collections and maps with equal keys and value classes,
     * and for block entities the same class (vanilla's dummies are overwritten before each use). Otherwise the first
     * differing field.
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
