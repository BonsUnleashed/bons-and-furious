package bons.furious.mixin.arsnouveau_mop49;

import bons.furious.patch.arsnouveau_mop49.DynamicLightHook;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * arsnouveau_dynamic_light_hook_lean (Ars Nouveau 4.12.7, client; since 1.0.49). With this switch Ars Nouveau's
 * com.hollingsworth.arsnouveau.common.mixin.light.LevelRendererMixin is left out (its "cancel" line). Its only handler was
 * a cancellable @Inject at the TAIL (the last return) of LevelRenderer.getLightColor(BlockAndTintGetter, BlockState,
 * BlockPos) with a CallbackInfoReturnable built on every call; this @ModifyReturnValue sits at the same return, at the
 * same mixin priority (1000), so it sees the same value after the injectors that run before it (lower priorities) and
 * makes the same calls (DynamicLightHook.light) on the plain int. The early return of emissive blocks is not touched, as
 * before.
 */
@Mixin(value = LevelRenderer.class, remap = false)
public abstract class LightColorHookMixin {
    @ModifyReturnValue(method = "m_109537_(Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;)I",
            at = @At("TAIL"))
    private static int bons$arsnouveauMop49Light(int light, @Local(argsOnly = true) BlockAndTintGetter level, @Local(argsOnly = true) BlockPos pos) {
        return DynamicLightHook.light(light, level, pos);
    }
}
