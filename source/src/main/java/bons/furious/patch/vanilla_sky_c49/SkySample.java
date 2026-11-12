package bons.furious.patch.vanilla_sky_c49;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.CubicSampler;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.phys.Vec3;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switches vanilla_sky_color_sample_memo (exact), vanilla_sky_color_sample_tick_memo and
 * vanilla_fog_color_sample_tick_memo (trade-offs, off by default) (Minecraft 1.21.1, client). Mojang code is no-copy:
 * injectors only. Ours, ideas from Collections Of Optimizations (vanilla.memoSkyColour, vanilla.memoSkyColourPerTick,
 * vanilla.memoBiomeFogColour), idea text only.
 *
 * What the game does: ClientLevel.getSkyColor(pos, partialTick) blends 216 biome sky colours around the camera
 * (CubicSampler.gaussianSampleVec3 over a 6x6x6 quart neighbourhood of pos.subtract(2, 2, 2).scale(0.25); its fetcher, the
 * lambda lambda$getSkyColor$12, reads Vec3.fromRGB24(biomeManager.getNoiseBiomeAtQuart(x, y, z).value().getSkyColor()))
 * and then applies time of day, rain, thunder and the lightning flash. It runs more than once per frame with the same
 * camera: LevelRenderer.renderSky, FogRenderer.setupColor (from LevelRenderer.renderLevel), shader packs' sky uniforms,
 * Distant Horizons. Sodium 0.6.13 and Embeddium 1.0.15 replace that blend call with their FastCubicSampler (@Redirect of the
 * same call, reading the same level's noise biomes); the wrappers here go around whichever call is there.
 * FogRenderer.setupColor makes the same kind of blend for the fog colour, each colour passed through the dimension's
 * getBrightnessDependentFogColor with the brightness its fetcher (lambda$setupColor$0) captures.
 *
 * vanilla_sky_color_sample_memo (exact): within one frame (GameRenderer.render, render thread only), the sample for the same
 * level, biome manager and sample position (exact bits) is the same immutable Vec3; everything after the sample (time of
 * day, rain, thunder, flash, partial tick) still runs on every call. Why identical: the sample's fetcher reads only the
 * biome manager's noise biomes and Biome.getSkyColor (a field of the biome's special effects); the client's chunk and biome
 * data only change while packets are handled (Minecraft.runTick runs the queued tasks before GameRenderer.render), never
 * inside GameRenderer.render.
 *
 * vanilla_sky_color_sample_tick_memo (trade-off): the sample is kept for a whole client tick for the same level and biome
 * manager, wherever the camera is in that tick. What the player can notice: while the camera crosses biomes, the biome
 * part of the sky colour follows it with a delay of at most one client tick (a twentieth of a second); the blend spans 24
 * blocks, so one tick of movement changes it only slightly. Time of day, rain, thunder and lightning still follow every
 * frame. vanilla_fog_color_sample_tick_memo: the same for the fog colour's blend (keyed also by the brightness its fetcher
 * captures), for dimensions with vanilla special effects only (OverworldEffects, NetherEffects, EndEffects, whose
 * getBrightnessDependentFogColor is a pure function of colour and brightness).
 *
 * Flags: -Dbons_and_furious.vanillaSkyColorSampleMemo=false, -Dbons_and_furious.vanillaSkyColorSampleTickMemo=false,
 * -Dbons_and_furious.vanillaFogColorSampleTickMemo=false.
 */
public final class SkySample {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.vanillaSkyColorSampleMemo", "true"));
    public static volatile boolean skyTickEnabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.vanillaSkyColorSampleTickMemo", "true"));
    public static volatile boolean fogTickEnabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.vanillaFogColorSampleTickMemo", "true"));
    /** Shadow (rigs, the exact memo only): -Dbons_and_furious.vanillaSkyColorSampleMemo.shadow=true samples again on every hit and compares. */
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.vanillaSkyColorSampleMemo.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    private static final Set<String> VANILLA_EFFECTS = Set.of("net.minecraft.client.renderer.DimensionSpecialEffects$OverworldEffects",
            "net.minecraft.client.renderer.DimensionSpecialEffects$NetherEffects", "net.minecraft.client.renderer.DimensionSpecialEffects$EndEffects");
    private static volatile boolean announced, skyTickAnnounced, fogTickAnnounced;

    // frame bracket (render thread only)
    private static volatile Thread renderThread;
    private static long frame;
    private static boolean inFrame;
    // per-frame sky memo
    private static long memoFrame = -1, px, py, pz;
    private static Object memoLevel, memoManager;
    private static Vec3 memoValue;
    // per-tick memos: each switch has its own client tick counter (its own Minecraft.tick hook)
    private static long skyTicks, fogTicks;
    private static long skyTickKey = -1;
    // since 1.0.49.2 the per-tick memos hold the level and its biome manager weakly (a new reference only when they change): the
    // strong fields kept the last ClientLevel after the player left the world
    private static final java.lang.ref.WeakReference<Object> NONE = new java.lang.ref.WeakReference<>(null);
    private static java.lang.ref.WeakReference<Object> skyTickLevel = NONE, skyTickManager = NONE;
    private static Vec3 skyTickValue;
    private static long fogTickKey = -1;
    private static int fogTickBrightness;
    private static java.lang.ref.WeakReference<Object> fogTickLevel = NONE, fogTickManager = NONE;
    private static Vec3 fogTickValue;

    private SkySample() {
    }

    /** Start of GameRenderer.render. */
    public static void beginFrame() {
        renderThread = Thread.currentThread();
        frame++;
        inFrame = true;
    }

    /** Return of GameRenderer.render. */
    public static void endFrame() {
        if (renderThread != Thread.currentThread()) return;
        inFrame = false;
        memoLevel = memoManager = null;
        memoValue = null;
    }

    /** Start of Minecraft.tick, for the sky tick memo. */
    public static void skyTick() {
        skyTicks++;
    }

    /** Start of Minecraft.tick, for the fog tick memo. */
    public static void fogTick() {
        fogTicks++;
    }

    /** vanilla_sky_color_sample_memo: in place of CubicSampler.gaussianSampleVec3(pos, fetcher) inside getSkyColor. */
    public static Vec3 sky(Vec3 pos, CubicSampler.Vec3Fetcher fetcher, Operation<Vec3> original, ClientLevel level, BiomeManager manager) {
        if (!enabled || !inFrame || renderThread != Thread.currentThread() || level == null) return original.call(pos, fetcher);
        long x = Double.doubleToRawLongBits(pos.x), y = Double.doubleToRawLongBits(pos.y), z = Double.doubleToRawLongBits(pos.z);
        if (memoFrame == frame && memoLevel == level && memoManager == manager && px == x && py == y && pz == z) {
            if (SHADOW) {
                Vec3 fresh = original.call(pos, fetcher);
                SHADOW_CHECKS.incrementAndGet();
                if (Double.doubleToRawLongBits(fresh.x) != Double.doubleToRawLongBits(memoValue.x)
                        || Double.doubleToRawLongBits(fresh.y) != Double.doubleToRawLongBits(memoValue.y)
                        || Double.doubleToRawLongBits(fresh.z) != Double.doubleToRawLongBits(memoValue.z)) {
                    long m = SHADOW_MISMATCHES.incrementAndGet();
                    if (m <= 20) LOGGER.warn("Bons and Furious: vanilla_sky_color_sample_memo shadow mismatch #{}: kept {} vs sampled {}", m, memoValue, fresh);
                }
            }
            return memoValue;
        }
        Vec3 v = original.call(pos, fetcher);
        memoFrame = frame;
        memoLevel = level;
        memoManager = manager;
        px = x;
        py = y;
        pz = z;
        memoValue = v;
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: vanilla_sky_color_sample_memo applies (the sky colour's 216-biome blend is sampled once per frame and camera position)");
        }
        return v;
    }

    /** vanilla_sky_color_sample_tick_memo: around the same call, outermost: one sample per client tick and level. */
    public static Vec3 skyPerTick(Vec3 pos, CubicSampler.Vec3Fetcher fetcher, Operation<Vec3> original, ClientLevel level, BiomeManager manager) {
        if (!skyTickEnabled || level == null || !onRenderThread()) return original.call(pos, fetcher);
        if (skyTickKey == skyTicks && skyTickLevel.get() == level && skyTickManager.get() == manager) return skyTickValue;
        Vec3 v = original.call(pos, fetcher);
        skyTickKey = skyTicks;
        skyTickLevel = ref(skyTickLevel, level);
        skyTickManager = ref(skyTickManager, manager);
        skyTickValue = v;
        if (!skyTickAnnounced) {
            skyTickAnnounced = true;
            LOGGER.info("Bons and Furious: vanilla_sky_color_sample_tick_memo applies (the sky colour's biome blend is sampled once per client tick)");
        }
        return v;
    }

    /** vanilla_fog_color_sample_tick_memo: around the fog colour's sample in FogRenderer.setupColor. */
    public static Vec3 fogPerTick(Vec3 pos, CubicSampler.Vec3Fetcher fetcher, Operation<Vec3> original, ClientLevel level, float brightness) {
        if (!fogTickEnabled || level == null || !onRenderThread() || !VANILLA_EFFECTS.contains(level.effects().getClass().getName()))
            return original.call(pos, fetcher);
        BiomeManager manager = level.getBiomeManager();
        int b = Float.floatToRawIntBits(brightness);
        if (fogTickKey == fogTicks && fogTickLevel.get() == level && fogTickManager.get() == manager && fogTickBrightness == b) return fogTickValue;
        Vec3 v = original.call(pos, fetcher);
        fogTickKey = fogTicks;
        fogTickLevel = ref(fogTickLevel, level);
        fogTickManager = ref(fogTickManager, manager);
        fogTickBrightness = b;
        fogTickValue = v;
        if (!fogTickAnnounced) {
            fogTickAnnounced = true;
            LOGGER.info("Bons and Furious: vanilla_fog_color_sample_tick_memo applies (the fog colour's biome blend is sampled once per client tick)");
        }
        return v;
    }

    /** The same reference while it still names {@code now}, else a new one (allocates only when the level or manager changes). */
    private static java.lang.ref.WeakReference<Object> ref(java.lang.ref.WeakReference<Object> old, Object now) {
        return old.get() == now ? old : new java.lang.ref.WeakReference<>(now);
    }

    /** The tick memos keep their samples in plain fields: render thread only (any other thread samples as before). */
    private static boolean onRenderThread() {
        return RenderSystem.isOnRenderThread();
    }

    /** Harness: forget every kept sample. */
    public static void reset() {
        memoFrame = -1;
        memoLevel = memoManager = null;
        memoValue = null;
        skyTickKey = -1;
        skyTickLevel = skyTickManager = NONE;
        skyTickValue = null;
        fogTickKey = -1;
        fogTickLevel = fogTickManager = NONE;
        fogTickValue = null;
    }
}
