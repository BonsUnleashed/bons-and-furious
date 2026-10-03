package bons.furious.patch.vanilla;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.Set;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.CubicSampler;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.phys.Vec3;

/**
 * Bons and Furious switch vanilla_fog_color_sample_memo (vanilla 1.20.1 client). SRG member names.
 *
 * FogRenderer.setupColor blends 216 biome fog colours around the camera (CubicSampler.gaussianSampleVec3 over a 6x6x6
 * quart neighbourhood, each colour through the dimension's brightness-dependent fog colour). In this pack setupColor runs
 * up to three times per frame with the same camera, level and partial tick: from vanilla, from Distant Horizons' fog
 * colour, and from Ars Nouveau's sky handler; the render distance and darkening, which differ between those calls, do not
 * enter the sample.
 *
 * Within one frame (GameRenderer.render, render thread only; chunk and biome data only change while packets are handled,
 * between frames) the sample is remembered with everything its fetcher depends on: the level, its biome manager, the
 * brightness the fetcher was built with (exact bits) and the sample position (exact bits). A later call in the same frame
 * with the same key gets the same immutable Vec3. Everything else in setupColor (the fog colour statics, clearColor,
 * Forge's fog colour event) still runs on every call. Only dimensions with vanilla special effects are remembered: another
 * mod's effects might not be a pure function of colour and brightness.
 */
public final class FogSample {
    /** Runtime switch (the config switch acts when classes are transformed). -Dbons_and_furious.fogSampleMemo=false also turns it off. */
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.fogSampleMemo", "true"));
    private static final Set<String> VANILLA_EFFECTS = Set.of("net.minecraft.client.renderer.DimensionSpecialEffects$OverworldEffects",
            "net.minecraft.client.renderer.DimensionSpecialEffects$NetherEffects", "net.minecraft.client.renderer.DimensionSpecialEffects$EndEffects");
    // render thread only (checked)
    private static volatile Thread renderThread;
    private static long frame;
    private static boolean inFrame;
    private static long memoFrame = -1, px, py, pz;
    private static int memoBrightness;
    private static Object memoLevel, memoManager;
    private static Vec3 memoValue;

    private FogSample() {
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

    /** In place of CubicSampler.gaussianSampleVec3(pos, fetcher) inside setupColor. */
    public static Vec3 sample(Vec3 pos, CubicSampler.Vec3Fetcher fetcher, Operation<Vec3> original, ClientLevel level, float brightness) {
        if (!enabled || !inFrame || renderThread != Thread.currentThread() || level == null
                || !VANILLA_EFFECTS.contains(level.effects().getClass().getName())) return original.call(pos, fetcher);
        BiomeManager manager = level.getBiomeManager();
        int b = Float.floatToRawIntBits(brightness);
        long x = Double.doubleToRawLongBits(pos.x), y = Double.doubleToRawLongBits(pos.y), z = Double.doubleToRawLongBits(pos.z);
        if (memoFrame == frame && memoLevel == level && memoManager == manager && memoBrightness == b && px == x && py == y && pz == z) return memoValue;
        Vec3 v = original.call(pos, fetcher);
        memoFrame = frame;
        memoLevel = level;
        memoManager = manager;
        memoBrightness = b;
        px = x;
        py = y;
        pz = z;
        memoValue = v;
        return v;
    }
}
