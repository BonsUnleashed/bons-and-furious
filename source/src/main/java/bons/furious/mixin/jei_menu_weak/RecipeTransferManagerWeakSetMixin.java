package bons.furious.mixin.jei_menu_weak;

import bons.furious.patch.jei_menu_weak.WeakMenuSet;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.Set;
import mezz.jei.library.recipes.RecipeTransferManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * jei_unsupported_menu_weak_set (JEI 15.59.0.212, MIT; client only). Fix. The two calls getMenuType makes on JEI's
 * unsupportedContainers HashSet (contains, add) go to a {@link WeakMenuSet} kept per RecipeTransferManager, which answers
 * exactly as the HashSet but no longer keeps unreachable menus - and through them old players and worlds - alive. With the
 * runtime flag off both calls reach the HashSet unchanged. Our code only; no JEI code is carried.
 */
@Mixin(value = RecipeTransferManager.class, remap = false)
public abstract class RecipeTransferManagerWeakSetMixin {
    @Unique
    private WeakMenuSet bons$unsupported;   // render thread only, like the HashSet it stands in for

    @Unique
    private WeakMenuSet bons$unsupported() {
        WeakMenuSet s = this.bons$unsupported;
        if (s == null) this.bons$unsupported = s = new WeakMenuSet();
        return s;
    }

    @WrapOperation(method = "getMenuType", at = @At(value = "INVOKE", target = "Ljava/util/Set;contains(Ljava/lang/Object;)Z"))
    private boolean bons$weakContains(Set<?> set, Object menu, Operation<Boolean> original) {
        if (!WeakMenuSet.enabled) return original.call(set, menu);
        return bons$unsupported().contains(menu) || original.call(set, menu);
    }

    @WrapOperation(method = "getMenuType", at = @At(value = "INVOKE", target = "Ljava/util/Set;add(Ljava/lang/Object;)Z"))
    private boolean bons$weakAdd(Set<?> set, Object menu, Operation<Boolean> original) {
        if (!WeakMenuSet.enabled) return original.call(set, menu);
        return bons$unsupported().add(menu);
    }
}
