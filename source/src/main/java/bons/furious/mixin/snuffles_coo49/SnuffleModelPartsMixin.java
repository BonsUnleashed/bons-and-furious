package bons.furious.mixin.snuffles_coo49;

import bons.furious.patch.snuffles_coo49.SnufflesCoo49;
import com.google.common.collect.ImmutableList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * snuffles_model_parts_reuse (Snuffles 1.1.2 for NeoForge 1.21.1, MIT; client): @Redirect of the ImmutableList.of(body, four
 * legs) call in SnuffleModel.bodyParts: the model keeps the list it built (a @Unique field) and hands it out again while
 * the five parts it is asked for are the same objects as the kept list's elements (they are final fields set in the
 * constructor, so always after the first call); otherwise a new list as the original. Exact variant: the same
 * unmodifiable list object on every call instead of a new equal one; its caller (AgeableListModel.renderToBuffer) only
 * iterates it. Allocation-free on the kept path (no Operation object). Our own logic only.
 */
@Mixin(targets = "mod.schnappdragon.snuffles.client.model.SnuffleModel", remap = false)
public abstract class SnuffleModelPartsMixin {
    @Unique
    private ImmutableList<Object> bons$snufflesCoo49Parts;

    @Redirect(method = "bodyParts()Ljava/lang/Iterable;",
            at = @At(value = "INVOKE", target = "Lcom/google/common/collect/ImmutableList;of(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Lcom/google/common/collect/ImmutableList;"),
            require = 1, allow = 1)
    private ImmutableList<Object> bons$snufflesCoo49BodyParts(Object a, Object b, Object c, Object d, Object e) {
        ImmutableList<Object> kept = this.bons$snufflesCoo49Parts;
        if (SnufflesCoo49.partsEnabled && kept != null && kept.get(0) == a && kept.get(1) == b && kept.get(2) == c
                && kept.get(3) == d && kept.get(4) == e)
            return kept;
        ImmutableList<Object> fresh = ImmutableList.of(a, b, c, d, e);
        if (SnufflesCoo49.partsEnabled) {
            this.bons$snufflesCoo49Parts = fresh;
            SnufflesCoo49.announceParts();
        }
        return fresh;
    }
}
