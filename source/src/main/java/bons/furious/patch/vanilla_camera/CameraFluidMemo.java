package bons.furious.patch.vanilla_camera;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joml.Vector3f;

/**
 * Bons and Furious switch vanilla_camera_fluid_memo (Minecraft 1.20.1 client). SRG member names.
 *
 * Camera.getFluidInCamera() decides the fog type (water, lava, powder snow, none) from the camera and the blocks and fluids
 * at and around its near plane. In this pack about twenty callers ask for it every frame (vanilla fog and sky, Iris, DH,
 * Alex's Caves, Alex's Mobs, BCLib, TipsyLib, PneumaticCraft, Nyctophobia, Blast from the Past, ...), and six mixins wrap it:
 * Valkyrien Skies (sealed-area grace, a whole-method wrapper; ship water via two operation wrappers), Clockwork, Alex's
 * Caves (Bubbled), Blast from the Past (tar) and Snow! Real Magic (snow layers). Each call recomputed the near plane and up
 * to six fluid and five block lookups, the wrappers' ship searches and raycasts, and about 600 bytes of garbage.
 *
 * Within one render pass (GameRenderer.render, render thread only) the answer is remembered with everything the method and
 * its six wrappers read from the camera and the client: the camera object, initialized, the level, the position (exact
 * bits), the block position, the forwards/up/left vectors (exact bits), the window width and height and the FOV option (the
 * near plane's inputs). A later call in the same pass with the same key gets the same FogType. Everything else those
 * methods read (block and fluid states, entity effects, the camera type, Valkyrien Skies' ships, grace ticks and config, Snow!
 * Real Magic's config) changes only between render passes: packets are handled and the game ticks before GameRenderer.render
 * starts, and Valkyrien Skies moves its ships at the very start of render, before any caller asks. The one side effect a
 * remembered answer skips is Valkyrien Skies' isShipWater field, which a repeat call would set to the value it already holds.
 *
 * Safety nets: the switch stands down (WARN, original behaviour) when a mixin that was not part of the tested set merged
 * methods into Camera; re-entrant calls and calls from other threads or outside a render pass always run the original.
 * SHADOW MODE for rigs: -Dbons_and_furious.cameraFluidMemoShadow=true computes the original on EVERY call, returns it, and
 * counts the calls whose remembered answer would have differed (the first ten are logged with their key, and a summary
 * every minute), so a test flight proves the memo before anyone relies on it.
 */
public final class CameraFluidMemo {
    /** Runtime switch (the config switch acts when classes are transformed). -Dbons_and_furious.cameraFluidMemo=false also turns it off. */
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.cameraFluidMemo", "true"));
    /** Shadow mode (see the class comment): the original answers every call and the memo is only compared. */
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.cameraFluidMemoShadow");
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    private static final String MERGED = "org.spongepowered.asm.mixin.transformer.meta.MixinMerged";
    /**
     * Every mixin that merged methods into Camera in the offline neighbour check of 1.0.26 (all 17 indexed Camera mixins of
     * the pack applied together with ours, Mixin 0.8.5 + MixinExtras 0.5.0). A Camera that carries a mixin outside this set
     * (a new mod, or a new mixin in an updated one) turns the switch off at its first use.
     */
    public static final Set<String> TESTED_CAMERA_MIXINS = Set.of(
            "bons.furious.mixin.vanilla_camera.CameraFluidMemoMixin");

    // render thread only (checked); memo cleared at the end of every render pass
    private static volatile Thread renderThread;
    private static long frame;
    private static boolean inFrame;
    private static int depth;
    private static boolean checked;
    private static long mFrame = -1, mPx, mPy, mPz;
    private static Object mCamera, mLevel;
    private static boolean mInit;
    private static int mBx, mBy, mBz, mFx, mFy, mFz, mUx, mUy, mUz, mLx, mLy, mLz, mW, mH, mFov;
    private static FogType mValue;
    private static boolean announced;
    /** Statistics (render thread): calls answered from the memo, calls computed; shadow mode: would-be hits and mismatches. */
    public static long hits, misses, shadowCalls, shadowHits, shadowMismatches, frames;
    private static long lastSummary = System.nanoTime();

    private CameraFluidMemo() {
    }

    /** Start of GameRenderer.render. */
    public static void beginFrame() {
        renderThread = Thread.currentThread();
        frame++;
        frames++;
        inFrame = true;
    }

    /** Return of GameRenderer.render: forget the pass's answer (and with it the level and camera references). */
    public static void endFrame() {
        if (renderThread != Thread.currentThread()) return;
        inFrame = false;
        mCamera = mLevel = null;
        mValue = null;
        mFrame = -1;
        if (SHADOW && System.nanoTime() - lastSummary > 60_000_000_000L) {
            lastSummary = System.nanoTime();
            LOGGER.info("Bons and Furious: vanilla_camera_fluid_memo SHADOW: {} calls in {} render passes; {} would have been answered from the memo, "
                    + "{} of those differ from the original", shadowCalls, frames, shadowHits, shadowMismatches);
        }
    }

    /** The @WrapMethod handler body: the Camera fields are passed in by the mixin. */
    public static FogType fogType(Camera camera, Operation<FogType> original, boolean initialized, BlockGetter level, Vec3 pos, BlockPos block,
                                  Vector3f forwards, Vector3f up, Vector3f left) {
        if (!enabled || !inFrame || depth != 0 || renderThread != Thread.currentThread()) return original.call();
        if (!checked) {
            checked = true;
            if (!inventoryMatches()) return original.call();
        }
        Minecraft mc = Minecraft.getInstance();
        int w = mc.getWindow().getWidth(), h = mc.getWindow().getHeight(), fov = mc.options.fov().get();
        long px = Double.doubleToRawLongBits(pos.x), py = Double.doubleToRawLongBits(pos.y), pz = Double.doubleToRawLongBits(pos.z);
        int bx = block.getX(), by = block.getY(), bz = block.getZ();
        int fx = Float.floatToRawIntBits(forwards.x), fy = Float.floatToRawIntBits(forwards.y), fz = Float.floatToRawIntBits(forwards.z);
        int ux = Float.floatToRawIntBits(up.x), uy = Float.floatToRawIntBits(up.y), uz = Float.floatToRawIntBits(up.z);
        int lx = Float.floatToRawIntBits(left.x), ly = Float.floatToRawIntBits(left.y), lz = Float.floatToRawIntBits(left.z);
        boolean hit = mFrame == frame && mCamera == camera && mLevel == level && mInit == initialized && mPx == px && mPy == py && mPz == pz
                && mBx == bx && mBy == by && mBz == bz && mFx == fx && mFy == fy && mFz == fz && mUx == ux && mUy == uy && mUz == uz
                && mLx == lx && mLy == ly && mLz == lz && mW == w && mH == h && mFov == fov;
        if (hit && !SHADOW) {
            hits++;
            return mValue;
        }
        FogType value;
        depth++;
        try {
            value = original.call();
        } finally {
            depth--;
        }
        if (SHADOW) {
            shadowCalls++;
            if (hit) {
                shadowHits++;
                if (value != mValue && shadowMismatches++ < 10) {
                    LOGGER.warn("Bons and Furious: vanilla_camera_fluid_memo SHADOW MISMATCH {}: the memo would answer {} but the original answers {} "
                            + "(frame {}, camera {}, level {}, initialized {}, position {} {} {}, block {} {} {}, forwards {} {} {}, up {} {} {}, left {} {} {}, "
                            + "window {}x{}, fov {})", shadowMismatches, mValue, value, frame, camera.getClass().getName(), describe(level), initialized,
                            pos.x, pos.y, pos.z, bx, by, bz, forwards.x, forwards.y, forwards.z, up.x, up.y, up.z, left.x, left.y, left.z, w, h, fov);
                }
                return value;              // the memo keeps the pass's first answer, as it would without shadow mode
            }
        } else {
            misses++;
        }
        mFrame = frame;
        mCamera = camera;
        mLevel = level;
        mInit = initialized;
        mPx = px;
        mPy = py;
        mPz = pz;
        mBx = bx;
        mBy = by;
        mBz = bz;
        mFx = fx;
        mFy = fy;
        mFz = fz;
        mUx = ux;
        mUy = uy;
        mUz = uz;
        mLx = lx;
        mLy = ly;
        mLz = lz;
        mW = w;
        mH = h;
        mFov = fov;
        mValue = value;
        if (!announced) {
            announced = true;
            LOGGER.info(SHADOW ? "Bons and Furious: vanilla_camera_fluid_memo SHADOW MODE: the original answers every call; the memo is only compared "
                    + "(summary every minute, mismatches as WARN)"
                    : "Bons and Furious: vanilla_camera_fluid_memo: the camera's fluid check is computed once per render pass for the same camera state");
        }
        return value;
    }

    private static String describe(BlockGetter level) {
        return level instanceof Level l ? String.valueOf(l.dimension().location()) : String.valueOf(level);
    }

    /** True when every mixin merged into Camera belongs to the tested set; otherwise the switch stands down. */
    static boolean inventoryMatches() {
        Set<String> unknown = new TreeSet<>();
        try {
            for (Method m : Camera.class.getDeclaredMethods()) {
                for (Annotation a : m.getDeclaredAnnotations()) {
                    if (!a.annotationType().getName().equals(MERGED)) continue;
                    String mixin = String.valueOf(a.annotationType().getMethod("mixin").invoke(a));
                    if (!TESTED_CAMERA_MIXINS.contains(mixin)) unknown.add(mixin);
                }
            }
        } catch (Throwable t) {
            enabled = false;
            LOGGER.warn("Bons and Furious: vanilla_camera_fluid_memo stands down: could not list the mixins merged into Camera ({})", t.toString());
            return false;
        }
        if (unknown.isEmpty()) return true;
        enabled = false;
        LOGGER.warn("Bons and Furious: vanilla_camera_fluid_memo stands down: Camera carries mixins it was not tested with ({}); the fluid check runs "
                + "unchanged", String.join(", ", unknown));
        return false;
    }
}
