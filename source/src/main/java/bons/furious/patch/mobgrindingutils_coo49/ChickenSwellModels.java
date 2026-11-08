package bons.furious.patch.mobgrindingutils_coo49;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.resources.ResourceLocation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch mobgrindingutils_chicken_swell_model_reuse (since 1.0.49; Mob Grinding Utils 1.1.10+mc1.21.1;
 * client side). Helper of ChickenSwellModelMixin; no Mob Grinding Utils or Minecraft code is carried.
 *
 * What the mod does. RenderChickenSwell.renderChickenSwell (a RenderLivingEvent.Post listener) draws the swelling body over
 * every chicken that was fed chicken feed (its persistent data holds "shouldExplode"), on every frame:
 * ChickenBodyModel.createBodyLayer() builds the mesh definition from scratch, LayerDefinition.bakeRoot() bakes it into a
 * new ModelPart tree (cubes with their vertex arrays), new ChickenBodyModel(part) wraps it, and
 * RL.mc("textures/entity/chicken.png") (ResourceLocation.fromNamespaceAndPath) names the texture for
 * RenderType.entitySolid; then the model is rendered with renderToBuffer at the pose the method sets up.
 *
 * What the switch does. The layer definition, the baked part tree, the model and the texture location are made once (on
 * the first such frame, by the mod's own calls) and handed back on later frames. Identical output: createBodyLayer reads
 * only constants; nothing in the mod (or anywhere else: the part tree never leaves this method) moves, hides or scales a
 * part of it, and rendering only reads the parts' poses and cubes, so the reused model writes the same vertices as a
 * fresh one; RenderType.entitySolid looks render types up by the location's value, so an equal location gives the same
 * render type. Only those four values are reused: each is reused only when the call that would make it gets exactly the
 * input the reused value was made from (the reused layer, the reused part, the texture path).
 *
 * -Dbons_and_furious.mobgrindingutilsChickenSwellModelReuse=false builds everything every frame (the original).
 */
public final class ChickenSwellModels {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.mobgrindingutilsChickenSwellModelReuse", "true"));
    static final String TEXTURE = "textures/entity/chicken.png";
    private static volatile LayerDefinition layer;
    private static volatile ModelPart part;
    private static volatile Object model;
    private static volatile ResourceLocation texture;
    private static volatile boolean announced;

    private ChickenSwellModels() {
    }

    /** ChickenBodyModel.createBodyLayer(). */
    public static LayerDefinition layer(Operation<LayerDefinition> original) {
        if (!enabled) return original.call();
        LayerDefinition l = layer;
        if (l == null) {
            l = original.call();
            layer = l;
            if (!announced) {
                announced = true;
                LOGGER.info("Bons and Furious: mobgrindingutils_chicken_swell_model_reuse applies (the swelling chicken body is built once and reused)");
            }
        }
        return l;
    }

    /** LayerDefinition.bakeRoot() of that layer. */
    public static ModelPart part(LayerDefinition definition, Operation<ModelPart> original) {
        if (!enabled || definition == null || definition != layer) return original.call(definition);
        ModelPart p = part;
        if (p == null) {
            p = original.call(definition);
            part = p;
        }
        return p;
    }

    /** new ChickenBodyModel(part) of that part. */
    @SuppressWarnings("unchecked")
    public static <M> M model(ModelPart root, Operation<M> original) {
        if (!enabled || root == null || root != part) return original.call(root);
        Object m = model;
        if (m == null) {
            m = original.call(root);
            model = m;
        }
        return (M) m;
    }

    /** RL.mc(path) of the chicken texture. */
    public static ResourceLocation texture(String path, Operation<ResourceLocation> original) {
        if (!enabled || !TEXTURE.equals(path)) return original.call(path);
        ResourceLocation t = texture;
        if (t == null) {
            t = original.call(path);
            texture = t;
        }
        return t;
    }
}
