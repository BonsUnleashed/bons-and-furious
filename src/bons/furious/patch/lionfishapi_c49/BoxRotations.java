package bons.furious.patch.lionfishapi_c49;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joml.Quaternionf;

/**
 * Bons and Furious switch lionfishapi_box_rotation_reuse (Lionfish API 2.8, also checked on 3.0; client). Lionfish API is
 * LGPL; no Lionfish code is carried. Ours, idea from Collections Of Optimizations (lionfishapi.reuseBoxRotations), idea
 * text only.
 *
 * What Lionfish API does: every model box of Cataclysm's bosses, mobs and 3D items is positioned by translateAndRotate on
 * every frame. AdvancedModelBox.translateAndRotate calls poseStack.mulPose(Axis.ZP / YP / XP.rotation(angle)) for each
 * non-zero angle, and each rotation() is a new Quaternionf (rotationZ / rotationY / rotationX of the angle);
 * BasicModelPart.translateAndRotate uses new Quaternionf().rotationZYX(z, y, x). PoseStack.mulPose multiplies the pose and
 * normal matrices by the quaternion and keeps no reference to it.
 *
 * What the switch does: on the render thread those calls fill one reused Quaternionf with the same JOML method
 * (rotationZ / rotationY / rotationX / rotationZYX, each of which writes all four components), so mulPose receives the
 * same four floats; other threads, and any Axis other than Mojang's positive ZP / YP / XP constants, allocate as before.
 * The reused quaternion is handed straight to mulPose: nothing runs between its filling and that call.
 *
 * Flag: -Dbons_and_furious.lionfishapiBoxRotationReuse=false restores the allocations at runtime.
 */
public final class BoxRotations {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.lionfishapiBoxRotationReuse", "true"));
    private static final Quaternionf SCRATCH = new Quaternionf();
    private static volatile boolean announced;

    private BoxRotations() {
    }

    /** In place of axis.rotation(angle) (m_252961_) in AdvancedModelBox.translateAndRotate. */
    public static Quaternionf rotation(Axis axis, float angle) {
        if (enabled && RenderSystem.isOnRenderThread()) {
            if (axis == Axis.f_252403_) return announce(SCRATCH.rotationZ(angle));
            if (axis == Axis.f_252436_) return announce(SCRATCH.rotationY(angle));
            if (axis == Axis.f_252529_) return announce(SCRATCH.rotationX(angle));
        }
        return axis.m_252961_(angle);
    }

    /** In place of new Quaternionf() in BasicModelPart.translateAndRotate (followed there by rotationZYX). */
    public static Quaternionf fresh() {
        if (enabled && RenderSystem.isOnRenderThread()) return announce(SCRATCH);
        return new Quaternionf();
    }

    private static Quaternionf announce(Quaternionf q) {
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: lionfishapi_box_rotation_reuse applies (Lionfish model boxes rotate through one reused quaternion)");
        }
        return q;
    }
}
