package bons.furious.patch.geckolib_lean;

import bons.furious.mixin.geckolib_lean.AnimationControllerTickOffsetAccessor;
import bons.pure.config.PureConfig;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.systems.RenderSystem;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.Collection;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.Tags;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joml.Matrix4f;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.core.animatable.model.CoreGeoBone;
import software.bernie.geckolib.core.animatable.model.CoreGeoModel;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.Animation;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationProcessor;
import software.bernie.geckolib.core.animation.AnimationState;

/**
 * Bons and Furious switch geckolib_distant_pose_hold (GeckoLib 4.8.4, client; since 1.0.49; kind change, OFF by default,
 * with the companion value geckolib_distant_pose_hold.value = on-screen height in pixels). Idea: Collections Of
 * Optimizations (geckolib.holdDistantPosePixels), idea text only.
 *
 * What GeckoLib does. GeoModel.handleAnimations runs AnimationProcessor.tickAnimation for every animatable on every frame
 * (and again in a shader pack's shadow pass): every controller is processed and the model's bones, which all instances of
 * that model share, are posed for this one instance just before it is drawn.
 *
 * What the switch does. A small entity - less than the configured number of pixels tall on screen, from its hitbox height,
 * its distance to the camera, the projection's field of view (zoom included) and the window height - that is not a player,
 * not the camera entity, not riding or ridden, not a boss (forge:bosses, or an entity that cannot change dimensions), and
 * whose last real tickAnimation was in this same client tick for the same baked model: its own bone transforms saved right
 * after that run (rotation, position, scale and the changed flags of every registered bone) are written back instead of
 * running tickAnimation again. A pose is only ever restored onto the instance it was saved from (keyed by GeckoLib's
 * instance id, per GeoModel). The saved pose is not used when, after that run, any controller was transitioning, played a
 * triggered animation, played an animation with sound, particle or custom-instruction keyframes, or could reach the end of
 * its animation stage before the next tick; nor on the first tick, after an animation reload or a model switch. The time
 * bookkeeping, the molang setup and setCustomAnimations (head turning and other per-frame tweaks) still run every frame.
 * Stands down entirely when GeckoLib Animation Optimizer is installed.
 *
 * Trade-off (off by default): small distant mobs move their animated parts 20 times a second instead of every frame (at
 * most one tick behind), and an animation change they start (a mob begins walking) shows up to one tick later.
 *
 * -Dbons_and_furious.geckolibDistantPoseHold=false switches it off at run time.
 * -Dbons_and_furious.geckolibDistantPoseHold.shadow=true (verification runs): tickAnimation runs as without the switch;
 * SHADOW_CHECKS counts the frames that would have been held and SHADOW_MAX_DELTA_MILLI the largest difference (radians or
 * blocks x 1000) between the pose that would have been restored and the one GeckoLib computed.
 */
public final class PoseHold {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.geckolibDistantPoseHold", "true"));
    public static final boolean SHADOW = Boolean.getBoolean("bons_and_furious.geckolibDistantPoseHold.shadow");
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MAX_DELTA_MILLI = new AtomicLong();
    /** Harness/rig counters: frames restored from a saved pose, real tickAnimation runs. */
    public static long HELD, RAN;
    /**
     * Small = less than this many pixels tall on screen: geckolib_distant_pose_hold.value (1-256, default 24). Read once,
     * the first time a GeckoLib model animates through this switch's wrapper (only while the switch is on).
     */
    public static final int PIXELS = PureConfig.intValue("geckolib_distant_pose_hold", 24, 1, 256);
    private static final boolean OPTIMIZER = optimizerPresent();
    private static float lastCot;   // cot(fovY / 2) of the last perspective projection seen (render thread)
    private static boolean announced;

    /** One instance's pose right after its last real tickAnimation. */
    public static final class Capture {
        long tick = Long.MIN_VALUE;
        int epoch;
        boolean holdable;
        CoreGeoBone[] bones = new CoreGeoBone[0];
        float[] values = new float[0];
        byte[] flags = new byte[0];
    }

    /** The saved poses of one GeoModel's instances (render thread only). */
    public static final class Captures {
        final Long2ObjectOpenHashMap<Capture> byInstance = new Long2ObjectOpenHashMap<>();
        long lastPrune = Long.MIN_VALUE;

        public int size() {
            return byInstance.size();
        }
    }

    private PoseHold() {}

    private static boolean optimizerPresent() {
        try {
            for (var mod : net.minecraftforge.fml.loading.LoadingModList.get().getMods()) {
                String id = mod.getModId().toLowerCase(Locale.ROOT), name = String.valueOf(mod.getDisplayName()).toLowerCase(Locale.ROOT);
                if (name.contains("animation optimizer") || id.contains("animation_optimizer") || id.contains("animationoptimizer")) {
                    LOGGER.info("Bons and Furious: geckolib_distant_pose_hold steps aside: {} ({}) is installed", mod.getDisplayName(), mod.getModId());
                    return true;
                }
            }
        } catch (Throwable t) {
            // no mod list (offline tools): nothing to step aside for
        }
        return false;
    }

    /** GeoModel.handleAnimations' tickAnimation call, wrapped. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void tick(Captures captures, AnimationProcessor processor, GeoAnimatable animatable, CoreGeoModel model,
                            AnimatableManager manager, double animTime, AnimationState state, boolean crash, Operation<Void> original,
                            long instanceId) {
        if (!enabled || OPTIMIZER || !(animatable instanceof Entity entity) || !RenderSystem.isOnRenderThread()) {
            original.call(processor, animatable, model, manager, animTime, state, crash);
            return;
        }
        boolean candidate = candidate(entity);
        long now = gameTick();
        Capture held = candidate ? holdable(captures, processor, manager, now, instanceId) : null;
        if (held != null && !SHADOW) {
            restore(held);
            HELD++;
            if (!announced) {
                announced = true;
                LOGGER.info("Bons and Furious: geckolib_distant_pose_hold applies (GeckoLib mobs under {} pixels re-pose once per tick)", PIXELS);
            }
            return;
        }
        original.call(processor, animatable, model, manager, animTime, state, crash);
        RAN++;
        if (held != null) shadowCompare(held, processor);
        afterRun(captures, processor, manager, animTime, candidate, now, instanceId);
    }

    /** The saved pose to restore for this frame, or null when tickAnimation must run. */
    public static Capture holdable(Captures captures, AnimationProcessor<?> processor, AnimatableManager<?> manager, long now, long instanceId) {
        Capture c = captures.byInstance.get(instanceId);
        if (c == null || !c.holdable || c.tick != now || processor.reloadAnimations || manager.isFirstTick()) return null;
        return c.epoch == ((PoseHoldEpoch) processor).bons$poseEpoch() ? c : null;
    }

    /** After a real tickAnimation: save the pose (candidates only) and whether it may be restored later in this tick. */
    public static void afterRun(Captures captures, AnimationProcessor<?> processor, AnimatableManager<?> manager, double animTime,
                                boolean candidate, long now, long instanceId) {
        if (!candidate) {
            captures.byInstance.remove(instanceId);
            return;
        }
        Capture c = captures.byInstance.get(instanceId);
        if (c == null) captures.byInstance.put(instanceId, c = new Capture());
        Collection<CoreGeoBone> bones = processor.getRegisteredBones();
        int n = bones.size();
        if (c.bones.length != n) {
            c.bones = new CoreGeoBone[n];
            c.values = new float[9 * n];
            c.flags = new byte[n];
        }
        int i = 0;
        float[] v = c.values;
        for (CoreGeoBone b : bones) {
            if (i == n) break;
            c.bones[i] = b;
            int o = 9 * i;
            v[o] = b.getRotX(); v[o + 1] = b.getRotY(); v[o + 2] = b.getRotZ();
            v[o + 3] = b.getPosX(); v[o + 4] = b.getPosY(); v[o + 5] = b.getPosZ();
            v[o + 6] = b.getScaleX(); v[o + 7] = b.getScaleY(); v[o + 8] = b.getScaleZ();
            c.flags[i] = (byte) ((b.hasRotationChanged() ? 1 : 0) | (b.hasPositionChanged() ? 2 : 0) | (b.hasScaleChanged() ? 4 : 0));
            i++;
        }
        c.tick = now;
        c.epoch = ((PoseHoldEpoch) processor).bons$poseEpoch();
        c.holdable = i == n && steady(manager, animTime);
        if (now >= captures.lastPrune + 200) {   // forget instances not drawn for 100 ticks
            captures.lastPrune = now;
            long cutoff = now - 100;
            captures.byInstance.long2ObjectEntrySet().removeIf(e -> e.getValue().tick < cutoff);
        }
    }

    /** True when no controller can change course within the next tick in a way a held pose would hide. */
    @SuppressWarnings("rawtypes")
    static boolean steady(AnimatableManager<?> manager, double animTime) {
        for (Object o : manager.getAnimationControllers().values()) {
            AnimationController ctl = (AnimationController) o;
            AnimationController.State s = ctl.getAnimationState();
            if (s == AnimationController.State.TRANSITIONING || ctl.getTriggeredAnimation() != null) return false;
            if (s != AnimationController.State.RUNNING) continue;
            AnimationProcessor.QueuedAnimation current = ctl.getCurrentAnimation();
            if (current == null) return false;
            Animation animation = current.animation();
            Animation.Keyframes events = animation.keyFrames();
            if (events.sounds().length > 0 || events.particles().length > 0 || events.customInstructions().length > 0) return false;
            double speed = ctl.getAnimationSpeed();
            double at = speed * Math.max(animTime - ((AnimationControllerTickOffsetAccessor) ctl).bons$tickOffset(), 0.0);
            if (!(at + Math.abs(speed) < animation.length())) return false;   // the stage could end before the next tick
        }
        return true;
    }

    /** Writes the saved pose back onto the bones it was taken from. */
    public static void restore(Capture c) {
        float[] v = c.values;
        for (int i = 0; i < c.bones.length; i++) {
            CoreGeoBone b = c.bones[i];
            int o = 9 * i;
            b.setRotX(v[o]); b.setRotY(v[o + 1]); b.setRotZ(v[o + 2]);
            b.setPosX(v[o + 3]); b.setPosY(v[o + 4]); b.setPosZ(v[o + 5]);
            b.setScaleX(v[o + 6]); b.setScaleY(v[o + 7]); b.setScaleZ(v[o + 8]);
            b.resetStateChanges();
            int f = c.flags[i];
            if ((f & 1) != 0) b.markRotationAsChanged();
            if ((f & 2) != 0) b.markPositionAsChanged();
            if ((f & 4) != 0) b.markScaleAsChanged();
        }
    }

    /** Shadow mode: how far the pose that would have been restored is from the one GeckoLib just computed. */
    private static void shadowCompare(Capture c, AnimationProcessor<?> processor) {
        SHADOW_CHECKS.incrementAndGet();
        float[] v = c.values;
        double max = 0;
        for (int i = 0; i < c.bones.length; i++) {
            CoreGeoBone b = c.bones[i];
            int o = 9 * i;
            max = Math.max(max, Math.abs(b.getRotX() - v[o]));
            max = Math.max(max, Math.abs(b.getRotY() - v[o + 1]));
            max = Math.max(max, Math.abs(b.getRotZ() - v[o + 2]));
            max = Math.max(max, Math.abs(b.getPosX() - v[o + 3]) / 16.0);
            max = Math.max(max, Math.abs(b.getPosY() - v[o + 4]) / 16.0);
            max = Math.max(max, Math.abs(b.getPosZ() - v[o + 5]) / 16.0);
        }
        long milli = (long) Math.ceil(max * 1000.0);
        SHADOW_MAX_DELTA_MILLI.accumulateAndGet(milli, Math::max);
    }

    /** Small, not a player, the camera entity, riding or ridden, or a boss. */
    private static boolean candidate(Entity e) {
        Minecraft mc = Minecraft.m_91087_();
        if (e instanceof Player || e == mc.m_91288_() || e.m_20159_() || e.m_20160_() || !e.m_6072_()
                || e.m_6095_().m_204039_(Tags.EntityTypes.BOSSES)) return false;
        Matrix4f p = RenderSystem.getProjectionMatrix();
        if (p.m23() == -1.0f && p.m33() == 0.0f && p.m11() > 0.0f) lastCot = p.m11();   // perspective (not a shadow pass)
        float cot = lastCot;
        if (!(cot > 0.0f)) return false;
        Vec3 cam = mc.f_91063_.m_109153_().m_90583_();
        double height = e.m_20206_();
        double dx = e.m_20185_() - cam.f_82479_, dy = e.m_20186_() + height * 0.5 - cam.f_82480_, dz = e.m_20189_() - cam.f_82481_;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (!(distance > 0.5)) return false;
        return isSmall(height, distance, cot, mc.m_91268_().m_85442_());
    }

    /** Less than geckolib_distant_pose_hold.value pixels tall on screen. */
    public static boolean isSmall(double height, double distance, float cot, int windowHeight) {
        return pixels(height, distance, cot, windowHeight) < PIXELS;
    }

    /** On-screen height in pixels of `height` blocks at `distance` blocks with projection cot(fovY / 2) = `cot`. */
    public static double pixels(double height, double distance, float cot, int windowHeight) {
        return height * cot / distance * (windowHeight * 0.5);
    }

    private static long gameTick() {
        Minecraft mc = Minecraft.m_91087_();
        return mc.f_91073_ != null ? mc.f_91073_.m_46467_() : Long.MIN_VALUE + 1;
    }
}
