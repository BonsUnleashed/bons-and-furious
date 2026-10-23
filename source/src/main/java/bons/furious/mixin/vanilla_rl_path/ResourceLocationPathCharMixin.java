package bons.furious.mixin.vanilla_rl_path;

import bons.furious.patch.vanilla_rl_path.PathCharFast;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * vanilla_rl_path_char_fast (Minecraft 1.20.1 ResourceLocation, Forge 47.4.16 jar; with Oculus 1.8.0; client).
 *
 * isValidPath (isValidPath, made public by Forge's access transformer) calls validPathChar (validPathChar) once per character of
 * the path, and Oculus's cancellable HEAD hook on validPathChar allocates a CallbackInfoReturnable for each call. This
 * wraps that one call (MixinExtras @WrapOperation, so another mod's wrapper on the same call still chains): a character
 * in [a-z0-9_./-] is answered true without the call (the merged validPathChar answers true for it: vanilla accepts it and
 * Oculus's hook only ever answers true); every other character makes the original call. Nothing else in isValidPath
 * changes, so Oculus's DUMMY check and the other mods' RETURN hooks on it run as before. See PathCharFast. No Minecraft or
 * Oculus code is carried.
 */
@Mixin(value = ResourceLocation.class, remap = false)
public abstract class ResourceLocationPathCharMixin {
    @WrapOperation(method = "isValidPath", at = @At(value = "INVOKE", target = "Lnet/minecraft/resources/ResourceLocation;validPathChar(C)Z"),
            require = 1, allow = 1)
    private static boolean bons$pathChar(char c, Operation<Boolean> original) {
        return PathCharFast.pathChar(c, original);
    }
}
