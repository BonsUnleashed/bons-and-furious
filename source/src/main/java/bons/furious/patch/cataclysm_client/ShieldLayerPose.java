package bons.furious.patch.cataclysm_client;

import com.github.L_Ender.lionfishapi.client.model.Animations.ModelAnimator;
import com.github.L_Ender.lionfishapi.client.model.tools.AdvancedEntityModel;
import com.github.L_Ender.lionfishapi.client.model.tools.AdvancedModelBox;
import com.github.L_Ender.lionfishapi.server.animation.IAnimatedEntity;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch cataclysm_shield_layer_pose (L_Ender's Cataclysm + Lionfish API, 1.21.1 tested builds L_Ender's
 * Cataclysm 1.21.1-3.33 and lionfishapi-3.1; client, since 1.0.36). Ours; no Cataclysm code (CC-BY-NC-ND-4.0); Lionfish
 * API (LGPL-3.0) is only called.
 *
 * Every frame the Ignis and the Ignited Revenant are drawn, their shield layer copies the renderer's model properties into
 * a second model of the same class (built the same way, new Ignis_Model() / new Ignited_Revenant_Model()) and runs that
 * model's full setupAnim with the arguments the renderer has just used for its own model (Ignis_Model.animate is 56 KB of
 * bytecode, beyond HotSpot's compile limit, and runs interpreted); the Harbinger's shield layer runs setupAnim a second
 * time on the renderer's own model. setupAnim of these three models is a function of its arguments, the entity and the
 * models' default pose only (animate() starts with resetToDefaultPose() and animator.update(entity); the procedural part
 * reads the entity and the frame's partial tick), and nothing between the renderer's setupAnim and these layers changes
 * the renderer's model (its draw and its earlier layers only draw it: bytecode, guarded). So:
 *  - Ignis / Ignited Revenant: when the renderer's model finished setupAnim last with the same entity and the same five
 *    arguments (bit-equal), the layer model gets the same pose by copying every part's pose fields from the renderer's
 *    model (after animator.update(entity), which keeps the layer animator's entity reference what setupAnim would set);
 *  - Harbinger: the same check, then the repeated call is not made (the model already holds that pose).
 * Any other case runs setupAnim as shipped. Exact variant: the layer model's ModelAnimator keeps empty transform maps
 * instead of the keyframe transforms setupAnim would leave there; nothing reads them before its next update() clears them.
 *
 * Ported to 1.21.1: the three models' animate methods are the 3.16 ones instruction for instruction; their setupAnim reads
 * the partial tick through Minecraft.getTimer().getGameTimeDeltaPartialTick(true) instead of getFrameTime() (a field read
 * set once per frame, so the renderer's call and the layer's read the same value); the layer render methods, the renderers'
 * layer lists (same order) and render methods keep their shape; Lionfish 3.1 draws a model with root().render(...) and the
 * packed ARGB colour (BasicEntityModel.renderToBuffer, AdvancedModelBox.render / doRender), which writes no pose field,
 * like 2.8's; AdvancedModelBox and BasicModelPart declare the same fields as in 2.8, so the copy below still covers every
 * pose field. Code unchanged.
 *
 * -Dbons_and_furious.cataclysmShieldLayerPose=false runs every setupAnim as before; .shadow=true runs them all and
 * compares the pose with the copy (SHADOW_CHECKS / SHADOW_MISMATCHES, WARN on the first 20 mismatches).
 */
public final class ShieldLayerPose {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.cataclysmShieldLayerPose", "true"));
    public static final boolean SHADOW = Boolean.parseBoolean(System.getProperty("bons_and_furious.cataclysmShieldLayerPose.shadow", "false"));
    public static final AtomicLong SHADOW_CHECKS = new AtomicLong(), SHADOW_MISMATCHES = new AtomicLong();
    private static volatile boolean announced;

    /** Implemented by the model mixins: the model's private ModelAnimator. */
    public interface Animated {
        ModelAnimator bons$animator();
    }

    // the last setupAnim one of the three models finished (render thread only)
    private static Object lastModel, lastEntity;
    private static int lastA, lastB, lastC, lastD, lastE;

    private ShieldLayerPose() {
    }

    public static void finished(Object model, Object entity, float a, float b, float c, float d, float e) {
        lastModel = model;
        lastEntity = entity;
        lastA = Float.floatToRawIntBits(a);
        lastB = Float.floatToRawIntBits(b);
        lastC = Float.floatToRawIntBits(c);
        lastD = Float.floatToRawIntBits(d);
        lastE = Float.floatToRawIntBits(e);
    }

    private static boolean posedWith(Object model, Object entity, float a, float b, float c, float d, float e) {
        return lastModel == model && lastEntity == entity && lastA == Float.floatToRawIntBits(a) && lastB == Float.floatToRawIntBits(b)
                && lastC == Float.floatToRawIntBits(c) && lastD == Float.floatToRawIntBits(d) && lastE == Float.floatToRawIntBits(e);
    }

    /** Ignis / Ignited Revenant shield layer: true when the layer model's pose was copied (its setupAnim is then not run). */
    public static boolean copied(AdvancedEntityModel<?> parent, AdvancedEntityModel<?> layer, IAnimatedEntity entity, float a, float b, float c, float d,
            float e) {
        if (!enabled || SHADOW || parent == layer || !posedWith(parent, entity, a, b, c, d, e)) return false;
        ((Animated) layer).bons$animator().update(entity);
        copyPose(parent, layer);
        announce();
        return true;
    }

    /** Harbinger shield layer: true when the renderer's model already holds the pose this setupAnim would give it. */
    public static boolean alreadyPosed(Object model, Object entity, float a, float b, float c, float d, float e) {
        if (!enabled || SHADOW || !posedWith(model, entity, a, b, c, d, e)) return false;
        announce();
        return true;
    }

    /** Shadow mode: would the switch have acted here? (asked before the shipped setupAnim runs and records itself) */
    public static boolean shadowWanted(Object parent, Object entity, float a, float b, float c, float d, float e) {
        return SHADOW && enabled && posedWith(parent, entity, a, b, c, d, e);
    }

    /** Shadow mode: called after the shipped setupAnim ran on the layer model (its pose must equal the renderer model's). */
    public static void shadowCompare(Object parent, Object layer, Object entity) {
        SHADOW_CHECKS.incrementAndGet();
        if (!samePose((AdvancedEntityModel<?>) parent, (AdvancedEntityModel<?>) layer)) {
            long n = SHADOW_MISMATCHES.incrementAndGet();
            if (n <= 20) LOGGER.warn("Bons and Furious: cataclysm_shield_layer_pose shadow mismatch #{} for {}", n, entity);
        }
    }

    /** Shadow mode for the Harbinger: the pose before the repeated setupAnim, compared afterwards. */
    public static float[] shadowSnapshot(Object model) {
        AdvancedEntityModel<?> m = (AdvancedEntityModel<?>) model;
        int n = 0;
        for (Object ignored : m.getAllParts()) n++;
        float[] out = new float[n * FIELDS];
        int i = 0;
        for (Object p : m.getAllParts()) i = write((AdvancedModelBox) p, out, i);
        return out;
    }

    public static void shadowCompareSnapshot(float[] before, Object model, Object entity) {
        SHADOW_CHECKS.incrementAndGet();
        float[] after = shadowSnapshot(model);
        if (!java.util.Arrays.equals(before, after)) {
            long n = SHADOW_MISMATCHES.incrementAndGet();
            if (n <= 20) LOGGER.warn("Bons and Furious: cataclysm_shield_layer_pose shadow mismatch #{} (repeated setupAnim) for {}", n, entity);
        }
    }

    static void copyPose(AdvancedEntityModel<?> from, AdvancedEntityModel<?> to) {
        Iterator<?> a = from.getAllParts().iterator(), b = to.getAllParts().iterator();
        while (a.hasNext() && b.hasNext()) copy((AdvancedModelBox) a.next(), (AdvancedModelBox) b.next());
    }

    /** Every public pose field of BasicModelPart / AdvancedModelBox (the harness compares every primitive field). */
    static void copy(AdvancedModelBox f, AdvancedModelBox t) {
        t.rotationPointX = f.rotationPointX;
        t.rotationPointY = f.rotationPointY;
        t.rotationPointZ = f.rotationPointZ;
        t.rotateAngleX = f.rotateAngleX;
        t.rotateAngleY = f.rotateAngleY;
        t.rotateAngleZ = f.rotateAngleZ;
        t.xScale = f.xScale;
        t.yScale = f.yScale;
        t.zScale = f.zScale;
        t.offsetX = f.offsetX;
        t.offsetY = f.offsetY;
        t.offsetZ = f.offsetZ;
        t.mirror = f.mirror;
        t.showModel = f.showModel;
        t.scaleChildren = f.scaleChildren;
        t.defaultRotationX = f.defaultRotationX;
        t.defaultRotationY = f.defaultRotationY;
        t.defaultRotationZ = f.defaultRotationZ;
        t.defaultOffsetX = f.defaultOffsetX;
        t.defaultOffsetY = f.defaultOffsetY;
        t.defaultOffsetZ = f.defaultOffsetZ;
        t.defaultPositionX = f.defaultPositionX;
        t.defaultPositionY = f.defaultPositionY;
        t.defaultPositionZ = f.defaultPositionZ;
        t.defaultscaleX = f.defaultscaleX;
        t.defaultscaleY = f.defaultscaleY;
        t.defaultscaleZ = f.defaultscaleZ;
    }

    private static final int FIELDS = 27;

    private static int write(AdvancedModelBox p, float[] o, int i) {
        o[i++] = p.rotationPointX;
        o[i++] = p.rotationPointY;
        o[i++] = p.rotationPointZ;
        o[i++] = p.rotateAngleX;
        o[i++] = p.rotateAngleY;
        o[i++] = p.rotateAngleZ;
        o[i++] = p.xScale;
        o[i++] = p.yScale;
        o[i++] = p.zScale;
        o[i++] = p.offsetX;
        o[i++] = p.offsetY;
        o[i++] = p.offsetZ;
        o[i++] = p.mirror ? 1 : 0;
        o[i++] = p.showModel ? 1 : 0;
        o[i++] = p.scaleChildren ? 1 : 0;
        o[i++] = p.defaultRotationX;
        o[i++] = p.defaultRotationY;
        o[i++] = p.defaultRotationZ;
        o[i++] = p.defaultOffsetX;
        o[i++] = p.defaultOffsetY;
        o[i++] = p.defaultOffsetZ;
        o[i++] = p.defaultPositionX;
        o[i++] = p.defaultPositionY;
        o[i++] = p.defaultPositionZ;
        o[i++] = p.defaultscaleX;
        o[i++] = p.defaultscaleY;
        o[i++] = p.defaultscaleZ;
        return i;
    }

    static boolean samePose(AdvancedEntityModel<?> x, AdvancedEntityModel<?> y) {
        Iterator<?> a = x.getAllParts().iterator(), b = y.getAllParts().iterator();
        float[] fa = new float[FIELDS], fb = new float[FIELDS];
        while (a.hasNext() && b.hasNext()) {
            write((AdvancedModelBox) a.next(), fa, 0);
            write((AdvancedModelBox) b.next(), fb, 0);
            for (int i = 0; i < FIELDS; i++) if (Float.floatToRawIntBits(fa[i]) != Float.floatToRawIntBits(fb[i])) return false;
        }
        return a.hasNext() == b.hasNext();
    }

    private static void announce() {
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: cataclysm_shield_layer_pose applies (Ignis / Ignited Revenant / Harbinger shield layers reuse the pose just computed)");
        }
    }
}
