package bons.furious.patch.cataclysm_client_c49;

import bons.furious.mixin.cataclysm_client_c49.ModelPartChildrenAccessor;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import java.util.function.Consumer;
import java.util.stream.Stream;
import net.minecraft.client.model.geom.ModelPart;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch cataclysm_pose_reset_loop (L_Ender's Cataclysm 3.16, 3.31 identical; client; since 1.0.49).
 * Idea: Collections Of Optimizations (cataclysm.leanPoseReset), idea text only. Our own logic; no Cataclysm or Minecraft
 * code (injectors only).
 *
 * The setupAnim of 21 Cataclysm models (Draugr, Scylla, Maledictus, Netherite Monstrosity, ...) starts with
 * {@code root().getAllParts().forEach(ModelPart::resetPose)}, every frame per drawn mob. Vanilla's getAllParts builds a
 * nested concat / flatMap stream per part (Embeddium's remove_streams turns it into one stream over a list).
 *
 * What changes: the getAllParts call answers no stream (the root is kept for the call), and the forEach right after it
 * walks the part tree itself (the part, then its children in their map's order, recursively: vanilla getAllParts' order)
 * calling resetPose on every part. Why it is identical: resetPose only copies a part's initial pose into its own fields,
 * so the result is the same set of parts reset to the same values (and in vanilla's order); the walk visits exactly the
 * parts of the tree. A tree with any part whose class is not ModelPart itself (another mod's part type, which may list
 * other parts or none) takes the original stream and forEach, as does every other forEach of the method.
 *
 * With Embeddium installed (its ModelPart mixin keeps a list of every part and getAllParts streams that list) the original
 * reset is already cheap (a 123-part model: 492 ns, against 2,392 ns for the walk), so the switch then stands aside and
 * Cataclysm's own reset runs; one INFO line says so.
 *
 * -Dbons_and_furious.cataclysmPoseResetLoop=false runs the original stream.
 */
public final class PoseReset {
    public static final String KEY = "cataclysm_pose_reset_loop";
    public static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.cataclysmPoseResetLoop", "true"));
    /** Embeddium's ModelPart mixin is in (ModelPart implements its part-list interface): stand aside. */
    public static final boolean EMBEDDIUM_PART_LIST = embeddiumPartList();
    private static volatile boolean announced;

    private PoseReset() {
    }

    private static boolean embeddiumPartList() {
        for (Class<?> i : ModelPart.class.getInterfaces())
            if (i.getName().equals("org.embeddedt.embeddium.render.entity.ModelPartExtended")) {
                LOGGER.info("Bons and Furious: {} stands aside: Embeddium already gives every model part a list of its parts, which makes Cataclysm's own pose reset cheap", KEY);
                return true;
            }
        return false;
    }

    /** The wrapped root().getAllParts() of setupAnim: null (the walk follows) for a tree of plain parts, else the stream. */
    public static Stream<ModelPart> allParts(ModelPart root, Operation<Stream<ModelPart>> original, LocalRef<ModelPart> kept) {
        if (!enabled || EMBEDDIUM_PART_LIST || !plain(root)) return original.call(root);
        kept.set(root);
        return null;
    }

    /** The wrapped forEach of setupAnim: the walk when the stream is ours (null after allParts), else the original. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void forEach(Stream stream, Consumer action, Operation<Void> original, LocalRef<ModelPart> kept) {
        ModelPart root = kept.get();
        if (stream != null || root == null) {
            original.call(stream, action);
            return;
        }
        kept.set(null);
        reset(root);
        if (!announced) {
            announced = true;
            LOGGER.info("Bons and Furious: {} applies (Cataclysm's models reset their part poses with a tree walk instead of a stream)", KEY);
        }
    }

    static boolean plain(ModelPart part) {
        if (part.getClass() != ModelPart.class) return false;
        for (ModelPart child : ((ModelPartChildrenAccessor) (Object) part).bons$cataclysmChildren().values()) if (!plain(child)) return false;
        return true;
    }

    static void reset(ModelPart part) {
        part.m_233569_();
        for (ModelPart child : ((ModelPartChildrenAccessor) (Object) part).bons$cataclysmChildren().values()) reset(child);
    }
}
