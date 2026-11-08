package bons.furious.mixin.pehkui_c49;

import bons.furious.patch.pehkui_c49.ModifierTypeMemo;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.function.Supplier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import virtuoel.pehkui.api.TypedScaleModifier;

/**
 * pehkui_typed_modifier_memo (Pehkui 3.8.3+1.21 for NeoForge, MIT; both sides): a MixinExtras @WrapOperation of the
 * Supplier.get call in TypedScaleModifier.getType(): the first non-null answer of one of Pehkui's own ScaleModifiers
 * suppliers is kept on the modifier and returned from then on (ModifierTypeMemo explains why that is the supplier's
 * answer); a modifier whose supplier is another mod's is marked once and its supplier is asked every time, as before.
 */
@Mixin(value = TypedScaleModifier.class, remap = false)
public abstract class TypedModifierMemoMixin {
    /** null = not decided yet, ModifierTypeMemo.NEVER = another mod's supplier, else the kept type. volatile: a reader on
     *  another thread gets the type with the happens-before of the thread that asked the supplier. */
    @Unique
    private volatile Object bons$pehkuiC49Type;

    @WrapOperation(method = "getType()Lvirtuoel/pehkui/api/ScaleType;",
            at = @At(value = "INVOKE", target = "Ljava/util/function/Supplier;get()Ljava/lang/Object;"), require = 1, allow = 1)
    private Object bons$pehkuiC49Type(Supplier<?> supplier, Operation<Object> original) {
        Object kept = this.bons$pehkuiC49Type;
        if (kept != null && ModifierTypeMemo.enabled) {
            if (kept == ModifierTypeMemo.NEVER) return original.call(supplier);
            if (ModifierTypeMemo.SHADOW) ModifierTypeMemo.shadow(kept, original.call(supplier));
            return kept;
        }
        Object answer = original.call(supplier);
        if (answer != null && ModifierTypeMemo.enabled) this.bons$pehkuiC49Type = ModifierTypeMemo.keepable(supplier) ? answer : ModifierTypeMemo.NEVER;
        return answer;
    }
}
