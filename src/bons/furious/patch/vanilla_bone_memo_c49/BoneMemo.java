package bons.furious.patch.vanilla_bone_memo_c49;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.systems.RenderSystem;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bons and Furious switch vanilla_model_bone_lookup_memo (Minecraft 1.20.1 client), a trade-off that is off by default.
 * Mojang code is no-copy: a wrapper only. Ours, idea from Collections Of Optimizations (vanilla.cacheModelPartLookups), idea
 * text only.
 *
 * What vanilla does: HierarchicalModel.getAnyDescendantWithName(name) (every animated bone of every keyframe animation of
 * every animated entity, every frame) walks the model's part tree for the first part that has a child of that name and
 * returns that child (vanilla_model_bone_lookup already replaced the stream with a plain walk; Embeddium has its own walk).
 *
 * What the switch does (only when bons_and_furious.properties turns it on): the part found for a name is remembered per
 * model, with the part it was found under; a later lookup of that name returns it at once when the model's root is still
 * the same object and that parent still holds exactly that part under that name; otherwise the walk runs again. Only
 * trees made of plain ModelParts (no class overriding getAllParts or hasChild) are remembered; lookups of "root" and
 * lookups that find nothing always walk. What can differ: a mod that adds a part of the same name to a part that comes
 * earlier in the walk, after the model was built (vanilla models never change once baked), keeps getting the old part
 * until the model is rebuilt (resource reload). Render thread only.
 *
 * Flag: -Dbons_and_furious.vanillaModelBoneLookupMemo=false walks on every lookup again at runtime.
 */
public final class BoneMemo {
    private static final Logger LOGGER = LogManager.getLogger("Bons and Furious");
    public static volatile boolean enabled = !"false".equalsIgnoreCase(System.getProperty("bons_and_furious.vanillaModelBoneLookupMemo", "true"));
    private static volatile boolean announced;

    /** The per-model memo, held by the model (a field the mixin adds). */
    public interface Holder {
        Map<String, Entry> bons$vanilla_bone_memo_c49Memo();
    }

    /** A part's child map (an accessor the mixin adds to ModelPart). */
    public interface Children {
        Map<String, ModelPart> bons$vanilla_bone_memo_c49Children();
    }

    /** parts[0] is the root, parts[i + 1] = parts[i].children.get(names[i]); the last part is the bone. */
    public record Entry(ModelPart[] parts, String[] names, Optional<ModelPart> answer) {
        boolean intact(ModelPart root) {
            if (parts[0] != root) return false;
            for (int i = 0; i < names.length; i++) if (((Children) (Object) parts[i]).bons$vanilla_bone_memo_c49Children().get(names[i]) != parts[i + 1]) return false;
            return true;
        }
    }

    private static final MethodType ALL_PARTS = MethodType.methodType(Stream.class);
    private static final MethodType HAS_CHILD = MethodType.methodType(boolean.class, String.class);
    private static final ClassValue<Boolean> PLAIN = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            try {
                MethodHandles.Lookup lookup = MethodHandles.publicLookup();
                return lookup.revealDirect(lookup.findVirtual(type, "m_171331_", ALL_PARTS)).getDeclaringClass() == ModelPart.class
                        && lookup.revealDirect(lookup.findVirtual(type, "m_233562_", HAS_CHILD)).getDeclaringClass() == ModelPart.class;
            } catch (Throwable t) {
                return false;
            }
        }
    };

    private BoneMemo() {
    }

    /** In place of HierarchicalModel.getAnyDescendantWithName (MixinExtras @WrapMethod around whichever body is there). */
    public static Optional<ModelPart> lookup(HierarchicalModel<?> model, String name, Operation<Optional<ModelPart>> original) {
        if (!enabled || name == null || name.equals("root") || !RenderSystem.isOnRenderThread()) return original.call(name);
        Map<String, Entry> memo = ((Holder) model).bons$vanilla_bone_memo_c49Memo();
        ModelPart root = model.m_142109_();
        Entry e = memo.get(name);
        if (e != null && e.intact(root)) return e.answer;
        Optional<ModelPart> found = original.call(name);
        if (found != null && found.isPresent() && root != null) {
            java.util.ArrayList<ModelPart> parts = new java.util.ArrayList<>();
            java.util.ArrayList<String> names = new java.util.ArrayList<>();
            boolean located;
            try {
                located = path(root, name, found.get(), parts, names);
            } catch (NotPlain np) {
                located = false;                 // a part class with its own traversal: never remembered
            }
            if (located) {
                memo.put(name, new Entry(parts.toArray(new ModelPart[0]), names.toArray(new String[0]), found));
                if (!announced) {
                    announced = true;
                    LOGGER.info("Bons and Furious: vanilla_model_bone_lookup_memo applies (model bones are looked up once per model and name)");
                }
            }
        }
        return found;
    }

    /**
     * The path from the root to the first part in walk order (pre-order, children in map order, as getAllParts) that
     * holds exactly this child under this name, ending with the child; false when there is none. Throws NotPlain for a
     * part class with its own traversal.
     */
    private static boolean path(ModelPart part, String name, ModelPart child, java.util.List<ModelPart> parts, java.util.List<String> names) {
        if (part.getClass() != ModelPart.class && !PLAIN.get(part.getClass())) throw new NotPlain();
        Map<String, ModelPart> children = ((Children) (Object) part).bons$vanilla_bone_memo_c49Children();
        parts.add(part);
        if (children.get(name) == child) {
            names.add(name);
            parts.add(child);
            return true;
        }
        for (Map.Entry<String, ModelPart> c : children.entrySet()) {
            names.add(c.getKey());
            if (path(c.getValue(), name, child, parts, names)) return true;
            names.remove(names.size() - 1);
        }
        parts.remove(parts.size() - 1);
        return false;
    }

    private static final class NotPlain extends RuntimeException {
        NotPlain() {
            super(null, null, false, false);
        }
    }

    /** Harness: a fresh memo map for a model. */
    public static Map<String, Entry> newMemo() {
        return new HashMap<>();
    }
}