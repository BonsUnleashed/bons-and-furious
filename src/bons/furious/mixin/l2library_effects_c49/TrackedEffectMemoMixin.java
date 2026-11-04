package bons.furious.mixin.l2library_effects_c49;

import bons.furious.patch.l2library_effects_c49.TrackedEffects;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.xkmc.l2library.init.events.EffectSyncEvents;
import net.minecraftforge.registries.tags.ITag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * l2library_tracked_effect_memo (L2 Library 2.5.1-slim and 2.5.5, both sides), part 2 of 2: the tag lookup in
 * EffectSyncEvents.tracked answers from an identity set kept with the tag's contents list (TrackedEffects explains why it
 * is the same answer). Injector only.
 */
@Mixin(value = EffectSyncEvents.class, remap = false)
public abstract class TrackedEffectMemoMixin {
    @SuppressWarnings({"rawtypes"})
    @WrapOperation(method = "tracked(Lnet/minecraft/world/effect/MobEffect;)Z", at = @At(value = "INVOKE",
            target = "Lnet/minecraftforge/registries/tags/ITag;contains(Ljava/lang/Object;)Z"), require = 1, allow = 1)
    private static boolean bons$trackedTag(ITag tag, Object effect, Operation<Boolean> original) {
        return TrackedEffects.contains(tag, effect, original);
    }
}
