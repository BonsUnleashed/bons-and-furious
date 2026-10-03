package bons.furious.mixin.emf_compile;

import bons.furious.patch.emf_compile.HierarchicalIdMemo;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.Map;
import org.spongepowered.asm.mixin.Mixin;
import traben.entity_model_features.EMFManager;
import traben.entity_model_features.models.jem_objects.EMFJemData;
import traben.entity_model_features.models.parts.EMFModelPart;
import traben.entity_model_features.models.parts.EMFModelPartRoot;

/**
 * emf_hierarchical_id_memo (Entity Model Features 3.2.4, client only).
 *
 * setupAnimationsFromJemToModel asks getModelFromHierarchicalId for the model id of every animation line (and the
 * expression parser asks for every part reference); for the many var.* / varb.* ids that are not parts the original scans
 * the whole part map with string operations and returns null. setupAnimationsFromJemToModel now runs inside a
 * HierarchicalIdMemo scope, and getModelFromHierarchicalId answers repeated ids for that setup's map from the scope.
 * The first lookup of every id and every lookup outside a scope run the original method unchanged. Why the answers are
 * identical: see HierarchicalIdMemo. EMF is LGPL-3.0; no EMF code is carried.
 */
@Mixin(value = EMFManager.class, remap = false)
public abstract class HierarchicalIdMemoMixin {
    @WrapMethod(method = "setupAnimationsFromJemToModel")
    private void bons$scope(EMFJemData jemData, EMFModelPartRoot root, int variantNum, Operation<Void> original) {
        HierarchicalIdMemo.Scope previous = HierarchicalIdMemo.enter();
        try {
            original.call(jemData, root, variantNum);
        } finally {
            HierarchicalIdMemo.leave(previous);
        }
    }

    @WrapMethod(method = "getModelFromHierarchicalId")
    private static EMFModelPart bons$remember(String hierarchId, Map<String, EMFModelPart> map, Operation<EMFModelPart> original) {
        Object remembered = HierarchicalIdMemo.cached(hierarchId, map);
        if (remembered != HierarchicalIdMemo.MISS) {
            if (HierarchicalIdMemo.VERIFY) HierarchicalIdMemo.verify(hierarchId, remembered, original.call(hierarchId, map));
            return (EMFModelPart) remembered;
        }
        EMFModelPart part = original.call(hierarchId, map);
        HierarchicalIdMemo.store(hierarchId, map, part);
        return part;
    }
}
