package bons.furious.mixin.simplyswords_coo49;

import bons.furious.patch.simplyswords_coo49.PityLootDeque;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.sweenus.simplyswords.loot.PityLootContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * simplyswords_pity_loot_deque_keep (Simply Swords 1.70.2-1.21.1): both ThreadLocal.remove() calls in PityLootContext.take()
 * are skipped, so the thread keeps its (empty) deque instead of building a new one on the next capture. See PityLootDeque
 * for why every result is identical. A WrapOperation composes with another mod's wrap or redirect of these calls.
 */
@Mixin(value = PityLootContext.class, remap = false)
public abstract class PityLootDequeKeepMixin {
    @WrapOperation(method = "take", at = @At(value = "INVOKE", target = "Ljava/lang/ThreadLocal;remove()V"), require = 2, allow = 2)
    private static void bons$keepEmptyPityDeque(ThreadLocal<?> local, Operation<Void> original) {
        if (!PityLootDeque.enabled) original.call(local);
    }
}
