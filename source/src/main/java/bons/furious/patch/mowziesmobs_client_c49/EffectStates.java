package bons.furious.patch.mowziesmobs_client_c49;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch mowziesmobs_effect_render_state_memo (Mowzie's Mobs; 1.21.1 tested build: Mowzie's Mobs
 * 1.21.1-1.8.2; client; since 1.0.49; an exact variant). Idea: Collections Of Optimizations
 * (mowziesmobs.cacheEffectRenderTypes), idea text only. Our own logic; no Mowzie's Mobs code (custom licence, wrappers only).
 *
 * MMRenderType.getGlowingEffect and getSolarFlare (sunstrikes, solar beams, supernovas, solar flares) build a new texture
 * shard, a state builder and a render state for their texture on every call, then a new render type from that state.
 *
 * What changes: the first call for a texture runs the original and keeps its render type's parameters and its state
 * (an immutable object); later calls for an equal texture make a new render type from the kept parameters and state,
 * through the same RenderType factory the original calls. Every call still returns a new render type object, so the
 * buffer source batches exactly as before. The one internal difference (exact variant): the render types of one texture
 * share their render state object instead of each having an equal copy. Anything that is not a plain composite render
 * type (another mod's change) is never kept.
 *
 * Minecraft 1.21.1: RenderType.create(String, VertexFormat, Mode, int, boolean, boolean, CompositeState) still builds a
 * CompositeRenderType whose outline is worked out from the state alone (the same for the kept state); the fields read here
 * keep their names under NeoForge's Mojang-named runtime (CompositeRenderType.state, RenderStateShard.name,
 * RenderType.sortOnUpload).
 *
 * -Dbons_and_furious.mowziesmobsEffectRenderStateMemo=false runs the originals. Shadow mode (.shadow=true): the original
 * runs on every call and is compared with ours (name, state text, format, mode, size, flags, outline; SHADOW_CHECKS /
 * SHADOW_MISMATCHES, up to 20 WARN lines).
 */
public final class EffectStates {
    public static final String KEY = "mowziesmobs_effect_render_state_memo";
    public static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.mowziesmobsEffectRenderStateMemo", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.mowziesmobsEffectRenderStateMemo.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    private static final Map<ResourceLocation, Kept> GLOW = new ConcurrentHashMap<>(), FLARE = new ConcurrentHashMap<>();
    private static volatile boolean broken, announced;
    private static MethodHandle create;
    private static Field name, sortOnUpload, state;

    private EffectStates() {
    }

    /** What the factory needs to make the render type again. */
    record Kept(String name, VertexFormat format, VertexFormat.Mode mode, int bufferSize, boolean crumbling, boolean sort, Object state) {
    }

    public static RenderType glow(ResourceLocation texture, Operation<RenderType> original) {
        return type(GLOW, texture, original);
    }

    public static RenderType flare(ResourceLocation texture, Operation<RenderType> original) {
        return type(FLARE, texture, original);
    }

    /** Kept render states in both tables (harness). */
    public static int kept() {
        return GLOW.size() + FLARE.size();
    }

    static RenderType type(Map<ResourceLocation, Kept> table, ResourceLocation texture, Operation<RenderType> original) {
        if (!enabled || broken || texture == null) return original.call(texture);
        Kept k = table.get(texture);
        if (k == null) {
            RenderType made = original.call(texture);
            Kept keep = keep(made);
            if (keep != null) table.putIfAbsent(texture, keep);
            return made;
        }
        RenderType ours;
        try {
            ours = (RenderType) create.invoke(k.name(), k.format(), k.mode(), k.bufferSize(), k.crumbling(), k.sort(), k.state());
        } catch (Throwable t) {
            broken = true;
            LOGGER.warn("Bons and Furious: {} stands aside: the render type factory failed ({}); Mowzie's Mobs builds its effect render types as before", KEY, t.toString());
            return original.call(texture);
        }
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: {} applies (Mowzie's Mobs effect render types reuse their render state)", KEY);
        }
        if (SHADOW) {
            RenderType stock = original.call(texture);
            long n = SHADOW_CHECKS.incrementAndGet();
            if (!same(ours, stock) && SHADOW_MISMATCHES.incrementAndGet() <= 20)
                LOGGER.warn("Bons and Furious: {} shadow mismatch #{} of {} for {}: ours {}, stock {}", KEY, SHADOW_MISMATCHES.get(), n, texture, ours, stock);
            return stock;
        }
        return ours;
    }

    /** True when the two render types carry the same parameters, state text and outline. */
    public static boolean same(RenderType a, RenderType b) {
        if (a == null || b == null || a.getClass() != b.getClass()) return false;
        return a.toString().equals(b.toString()) && a.format() == b.format() && a.mode() == b.mode() && a.bufferSize() == b.bufferSize()
                && a.affectsCrumbling() == b.affectsCrumbling() && a.isOutline() == b.isOutline() && a.outline().map(Object::toString).equals(b.outline().map(Object::toString))
                && sortOf(a) == sortOf(b);
    }

    private static boolean sortOf(RenderType t) {
        try {
            return sortOnUpload.getBoolean(t);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static synchronized Kept keep(RenderType made) {
        if (made == null || !made.getClass().getName().equals("net.minecraft.client.renderer.RenderType$CompositeRenderType")) return null;
        try {
            if (create == null) {
                Class<?> composite = made.getClass();
                Field st = composite.getDeclaredField("state");
                st.setAccessible(true);
                Field nm = RenderStateShard.class.getDeclaredField("name");
                nm.setAccessible(true);
                Field so = RenderType.class.getDeclaredField("sortOnUpload");
                so.setAccessible(true);
                Class<?>[] params = {String.class, VertexFormat.class, VertexFormat.Mode.class, int.class, boolean.class, boolean.class, st.getType()};
                Method m;
                try {
                    m = RenderType.class.getMethod("create", params);   // public under NeoForge's access transformer
                } catch (NoSuchMethodException notPublic) {
                    m = RenderType.class.getDeclaredMethod("create", params);
                    m.setAccessible(true);
                }
                MethodHandle h = MethodHandles.lookup().unreflect(m);
                create = h.asType(MethodType.methodType(Object.class, String.class, VertexFormat.class, VertexFormat.Mode.class, int.class, boolean.class, boolean.class, Object.class));
                state = st;
                name = nm;
                sortOnUpload = so;
            }
            return new Kept((String) name.get(made), made.format(), made.mode(), made.bufferSize(), made.affectsCrumbling(), sortOnUpload.getBoolean(made), state.get(made));
        } catch (Throwable t) {
            broken = true;
            LOGGER.warn("Bons and Furious: {} stands aside: the render type could not be read ({}); Mowzie's Mobs builds its effect render types as before", KEY, t.toString());
            return null;
        }
    }
}
