package bons.furious.mixin.structurify_c2;

import bons.furious.patch.structurify_c2.CheckMemo;
import com.faboslav.structurify.common.world.level.structure.checks.StructureChecker;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.Map;
import java.util.function.Function;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * structurify_check_memo_slim (Structurify 2.0.34+mc1.20.1, CC BY-NC-ND 4.0; both sides): the three inner computeIfAbsent
 * calls of the outer check lambda (synthetic lambda$checkStructure$3, fingerprinted by the guard) apply their check
 * directly instead of storing it in a map nothing reads (see {@link CheckMemo}). Spawn's HEAD inject on checkStructure is
 * unaffected.
 */
@Mixin(value = StructureChecker.class, remap = false)
public abstract class StructureCheckMemoMixin {
    @WrapOperation(method = "lambda$checkStructure$3", require = 3, allow = 3, at = @At(value = "INVOKE",
            target = "Ljava/util/Map;computeIfAbsent(Ljava/lang/Object;Ljava/util/function/Function;)Ljava/lang/Object;"))
    private static Object bons$innerCheckOnce(Map<Object, Object> map, Object id, Function<Object, Object> check, Operation<Object> original) {
        return CheckMemo.inner(map, id, check, original);
    }
}
