package bons.furious.mixin.tomeofblood_coo49;

import bons.furious.patch.tomeofblood_coo49.TobMimic;
import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * tomeofblood_mimic_index_guard (Tome of Blood: Rebirth 0.4.5, LGPL-3.0; both sides; fix): @Redirect of the List.get call in
 * AugmentMimic.applyModifiers, which reads spell.recipe.get(spellContext.getCurrentIndex() - 1) without a bounds check: an
 * index outside the recipe list answers null (no part in front, so Mimic applies only its own modifiers, as it does for a
 * part in front that is not an augment) instead of throwing IndexOutOfBoundsException out of spell resolution. In-range reads
 * make the same get call. A redirect (not a MixinExtras wrap) so the in-range path pays one bounds test and no operation
 * object. See TobMimic. Our own logic only.
 */
@Mixin(targets = "com.mystchonky.tomeofblood.common.glyphs.AugmentMimic", remap = false)
public abstract class TobMimicIndexMixin {
    @Redirect(method = "applyModifiers(Lcom/hollingsworth/arsnouveau/api/spell/SpellStats$Builder;Lcom/hollingsworth/arsnouveau/api/spell/AbstractSpellPart;Lnet/minecraft/world/phys/HitResult;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/LivingEntity;Lcom/hollingsworth/arsnouveau/api/spell/SpellContext;)Lcom/hollingsworth/arsnouveau/api/spell/SpellStats$Builder;",
            at = @At(value = "INVOKE", target = "Ljava/util/List;get(I)Ljava/lang/Object;"),
            require = 1, allow = 1, remap = false)
    private Object bons$tomeofbloodCoo49MimicPart(List<?> recipe, int index) {
        return TobMimic.part(recipe, index);
    }
}
