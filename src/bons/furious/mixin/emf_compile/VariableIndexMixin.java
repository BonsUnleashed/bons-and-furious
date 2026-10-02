package bons.furious.mixin.emf_compile;

import bons.furious.patch.emf_compile.VariableIndex;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import traben.entity_model_features.models.animation.math.asm.ASMVariableHandler;

/**
 * emf_variable_index (Entity Model Features 3.2.4, client only).
 *
 * EMF's animation compiler (ASMParser.compileOrNull) asks this handler for a variable's slot with List.contains then
 * List.indexOf on its float or bool name list, and after every animation line checks
 * floatVarList.stream().anyMatch(boolVarList::contains) - quadratic scans that cost about 4 s of render-thread time per
 * resource reload with Fresh Animations' player models. Each handler now carries a VariableIndex (two name -> index maps
 * and an "overlap seen" flag) that mirrors the lists: the three List calls in getAndAssignVarIndex and the anyMatch in
 * verifyEndOfParse answer from it. The appends still go to the real lists, the first two checks of verifyEndOfParse and
 * its exception are untouched, and a handler created while the switch is off has no index and runs the original calls.
 * Why the answers are identical: see VariableIndex. EMF is LGPL-3.0; no EMF code is carried.
 */
@Mixin(value = ASMVariableHandler.class, remap = false)
public abstract class VariableIndexMixin {
    @Shadow
    @Final
    private List<String> floatVarList;

    @Shadow
    @Final
    private List<String> boolVarList;

    @Unique
    private final VariableIndex bons$index = VariableIndex.create();

    @WrapOperation(method = "getAndAssignVarIndex", at = @At(value = "INVOKE", target = "Ljava/util/List;contains(Ljava/lang/Object;)Z"))
    private boolean bons$contains(List<String> list, Object name, Operation<Boolean> original) {
        VariableIndex index = this.bons$index;
        return index != null ? index.contains(list == this.boolVarList, name, list) : original.call(list, name);
    }

    @WrapOperation(method = "getAndAssignVarIndex", at = @At(value = "INVOKE", target = "Ljava/util/List;indexOf(Ljava/lang/Object;)I"))
    private int bons$indexOf(List<String> list, Object name, Operation<Integer> original) {
        VariableIndex index = this.bons$index;
        return index != null ? index.indexOf(list == this.boolVarList, name, list) : original.call(list, name);
    }

    @WrapOperation(method = "getAndAssignVarIndex", at = @At(value = "INVOKE", target = "Ljava/util/List;add(Ljava/lang/Object;)Z"))
    private boolean bons$add(List<String> list, Object name, Operation<Boolean> original) {
        boolean added = original.call(list, name);
        VariableIndex index = this.bons$index;
        if (index != null) index.added(list == this.boolVarList, name, list.size() - 1);
        return added;
    }

    @WrapOperation(method = "verifyEndOfParse", at = @At(value = "INVOKE", target = "Ljava/util/stream/Stream;anyMatch(Ljava/util/function/Predicate;)Z"))
    private boolean bons$overlap(Stream<String> floats, Predicate<? super String> inBools, Operation<Boolean> original) {
        VariableIndex index = this.bons$index;
        return index != null ? index.overlap(this.floatVarList, this.boolVarList) : original.call(floats, inBools);
    }
}
