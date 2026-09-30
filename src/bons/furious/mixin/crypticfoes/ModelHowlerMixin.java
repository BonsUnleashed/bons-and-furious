package bons.furious.mixin.crypticfoes;

import com.min01.crypticfoes.entity.living.EntityHowler;
import com.min01.crypticfoes.entity.model.ModelHowler;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * crypticfoes_howler_bone_cache (Cryptic Foes 1.0.4, client).
 *
 * Keyframe animation looks up every animated bone by name through HierarchicalModel.getAnyDescendantWithName, which walks
 * the whole part tree, for every bone on every frame. ModelHowler now overrides that lookup with a per-model cache of
 * found and missing names (at most 256 entries) that asks the original method once per name. A model's part tree never
 * changes after baking, and a newly baked model starts with an empty cache.
 *
 * This only adds a method and a field; nothing is injected into Cryptic Foes' render code.
 */
@Mixin(value = ModelHowler.class, remap = false)
public abstract class ModelHowlerMixin extends HierarchicalModel<EntityHowler> {
    /** Bone name -> result of the original lookup (empty when the model has no such bone). Created on first use. */
    @Unique
    private Map<String, Optional<ModelPart>> ac$boneCache;

    /** getAnyDescendantWithName with a per-model cache in front of it. */
    @Override
    public Optional<ModelPart> m_233393_(String name) {
        if (this.ac$boneCache == null) {
            this.ac$boneCache = new HashMap<>();
        }
        Optional<ModelPart> part = this.ac$boneCache.get(name);
        if (part != null) {
            return part;
        }
        part = super.m_233393_(name);
        if (this.ac$boneCache.size() < 256) {
            this.ac$boneCache.put(name, part);
        }
        return part;
    }
}
