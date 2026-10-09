package bons.furious.patch.lionfish_models;

import com.github.L_Ender.lionfishapi.client.model.Transform;
import com.github.L_Ender.lionfishapi.client.model.tools.AdvancedModelBox;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.function.Function;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch lionfish_model_animator (Lionfish API 2.8, client, since 1.0.36). Ours; no Lionfish code.
 *
 * Lionfish's ModelAnimator (used by 57 Cataclysm models; it does work only while an attack animation plays) copies its
 * current keyframe map into the previous one at every endKeyframe() and makes a new Transform for every animated part of
 * every keyframe. ModelAnimatorReuseMixin trades the two maps instead of copying and hands dropped Transforms, reset to
 * zero, to the next keyframe. Same map contents (keys, Transform values) and the same part transforms after every call;
 * the order in which a map is iterated cannot change a result because each loop adds one box's values to that box only.
 *
 * -Dbons_and_furious.lionfishModelAnimator=false copies and allocates as before.
 */
public final class LionfishAnimatorReuse {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.lionfishModelAnimator", "true"));
    /** Spare Transforms kept per animator at most (the largest Cataclysm model has 74 parts). */
    static final int SPARE_LIMIT = 256;
    private static volatile boolean announced;

    private LionfishAnimatorReuse() {
    }

    /** The Transforms a map is about to drop (no other map holds them) become spares. */
    public static void keep(ArrayDeque<Transform> spare, HashMap<AdvancedModelBox, Transform> map) {
        if (map.isEmpty()) return;
        for (Transform t : map.values()) {
            if (spare.size() >= SPARE_LIMIT) break;
            spare.push(t);
        }
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: lionfish_model_animator applies (Lionfish keyframes trade their two maps and reuse transforms)");
        }
    }

    /** transformMap.computeIfAbsent(box, b -> new Transform()) with a spare Transform reset to zero when one is kept. */
    public static Object transform(HashMap<AdvancedModelBox, Transform> map, AdvancedModelBox box, ArrayDeque<Transform> spare,
                                   Function<Object, Object> make, Operation<Object> original) {
        Transform t = map.get(box);
        if (t != null) return t;
        t = spare.poll();
        if (t == null) return original.call(map, box, make);
        t.resetRotation();
        t.resetOffset();
        map.put(box, t);
        return t;
    }
}
