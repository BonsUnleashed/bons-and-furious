package bons.furious.mixin.forge_registry_wrapper;

import bons.furious.patch.forge_registry_wrapper.WrapperGrowth;
import it.unimi.dsi.fastutil.objects.ObjectList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * forge_registry_wrapper_growth (Forge 47.4.16 and 47.4.28, both sides).
 *
 * NamespacedWrapper.onAdded grows its holdersById list with holdersById.size(Math.max(holdersById.size(), id + 1)), which
 * reallocates the list's array to exactly that size on every new id. Just before that call the list's capacity is raised to
 * the next power of two (WrapperGrowth.ahead); the size argument is handed on unchanged, so the call itself, its result and
 * the following set(id, holder) are Forge's own. The receiver of that call is this.holdersById, read by the same GETFIELD
 * right before it on the same thread. NamespacedDefaultedWrapper.onAdded calls this method through super. Forge is
 * LGPL-2.1; no Forge code is carried. A @ModifyArg rather than a wrapper: no allocation per registration, and it composes
 * with other mods' injectors on the same call.
 */
@Mixin(targets = "net.minecraftforge.registries.NamespacedWrapper", remap = false)
public abstract class WrapperGrowthMixin {
    @Shadow
    private ObjectList<?> holdersById;

    @ModifyArg(method = "onAdded", at = @At(value = "INVOKE", target = "Lit/unimi/dsi/fastutil/objects/ObjectList;size(I)V"), index = 0,
            require = 1, allow = 1)
    private int bons$growAhead(int newSize) {
        WrapperGrowth.ahead(this.holdersById, newSize);
        return newSize;
    }
}
