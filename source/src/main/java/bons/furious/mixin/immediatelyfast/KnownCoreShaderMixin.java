package bons.furious.mixin.immediatelyfast;

import bons.furious.patch.immediatelyfast.KnownCoreShaders;
import com.bawnorton.mixinsquared.TargetHandler;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.Optional;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * immediatelyfast_known_core_shaders (ImmediatelyFast 1.5.5+1.20.4).
 *
 * After every shader reload ImmediatelyFast's checkForCoreShaderModifications (its MixinGameRenderer handler) looks up
 * the source of each blacklisted core shader and switches HUD batching off when a non-vanilla pack supplies it. Each
 * lookup now passes through KnownCoreShaders: a file recognised by its exact bytes as batching-safe is reported as not
 * found, so it no longer counts against HUD batching. Every other file is returned unchanged and decided as before.
 */
@Mixin(value = GameRenderer.class, priority = 1500, remap = false)
public abstract class KnownCoreShaderMixin {
    @TargetHandler(mixin = "net.raphimc.immediatelyfast.injection.mixins.core.compat.MixinGameRenderer", name = "checkForCoreShaderModifications")
    @WrapOperation(method = "@MixinSquared:Handler", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/packs/resources/ResourceProvider;getResource(Lnet/minecraft/resources/ResourceLocation;)Ljava/util/Optional;"))
    private Optional<Resource> bons$skipKnownShaders(ResourceProvider provider, ResourceLocation id, Operation<Optional<Resource>> original) {
        return KnownCoreShaders.filter(id, original.call(provider, id));
    }
}
