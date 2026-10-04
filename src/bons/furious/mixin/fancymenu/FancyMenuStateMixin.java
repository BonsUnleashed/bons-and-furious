package bons.furious.mixin.fancymenu;

import bons.furious.patch.fancymenu.RenderThreadLocal;
import java.util.function.Supplier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * fancymenu_render_thread_state (FancyMenu 3.9.14, client; custom licence - our own logic only): the
 * ThreadLocal.withInitial call in the static initialiser of RenderScaleUtil, RenderTranslationUtil and RenderRotationUtil
 * builds a RenderThreadLocal with the same supplier (see there for why every thread sees the same values and objects).
 */
@Mixin(targets = {"de.keksuccino.fancymenu.util.rendering.RenderScaleUtil", "de.keksuccino.fancymenu.util.rendering.RenderTranslationUtil",
        "de.keksuccino.fancymenu.util.rendering.RenderRotationUtil"}, remap = false)
public abstract class FancyMenuStateMixin {
    @Redirect(method = "<clinit>", at = @At(value = "INVOKE", target = "Ljava/lang/ThreadLocal;withInitial(Ljava/util/function/Supplier;)Ljava/lang/ThreadLocal;"))
    private static <S> ThreadLocal<S> bons$renderThreadLocal(Supplier<? extends S> supplier) {
        return RenderThreadLocal.withInitial(supplier);
    }
}
