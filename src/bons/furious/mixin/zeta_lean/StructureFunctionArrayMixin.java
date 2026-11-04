package bons.furious.mixin.zeta_lean;

import bons.furious.patch.zeta_lean.StructureFunctions;
import java.util.Iterator;
import java.util.Set;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.violetmoon.zeta.util.handler.StructureBlockReplacementHandler;

/**
 * zeta_structure_function_array (Zeta 1.0-24, server side incl. the integrated server): in
 * StructureBlockReplacementHandler.getResultingBlockState the functions set's iterator() call (the for-each over the
 * replacement functions, once per structure block) gets an iterator over an array copy of the set in the set's own
 * iteration order (StructureFunctions); Zeta's loop, its tests and its first-non-null return are unchanged.
 * addReplacement's TAIL bumps the copy's epoch. A plain @Redirect: the call runs for every block a structure places, and
 * a WrapOperation's original call would allocate. No Zeta code is carried. No other mod's mixin targets this class (both
 * mixin indexes, 2026-10-28).
 */
@Mixin(value = StructureBlockReplacementHandler.class, remap = false)
public abstract class StructureFunctionArrayMixin {
    @Redirect(method = "getResultingBlockState", at = @At(value = "INVOKE", target = "Ljava/util/Set;iterator()Ljava/util/Iterator;"))
    private static Iterator<?> bons$functionArray(Set<?> functions) {
        return StructureFunctions.iterator(functions);
    }

    @Inject(method = "addReplacement", at = @At("TAIL"))
    private static void bons$functionsChanged(StructureBlockReplacementHandler.StructureFunction func, CallbackInfo ci) {
        StructureFunctions.bump();
    }
}
