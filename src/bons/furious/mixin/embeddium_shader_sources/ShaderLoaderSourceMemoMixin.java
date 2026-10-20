package bons.furious.mixin.embeddium_shader_sources;

import bons.furious.patch.embeddium_shader_sources.ShaderSources;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import me.jellysquid.mods.sodium.client.gl.shader.ShaderLoader;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;

/**
 * embeddium_shader_source_memo (Embeddium 0.3.31, client): ShaderLoader.getShaderSource, wrapped whole. ShaderSources keeps the
 * text of each shader read successfully and returns it on later calls; misses and read errors still run the original and throw
 * as before. Embeddium is LGPL-3.0-only; nothing of it is carried, only the memo around the original call.
 */
@Mixin(value = ShaderLoader.class, remap = false)
public abstract class ShaderLoaderSourceMemoMixin {
    @WrapMethod(method = "getShaderSource(Lnet/minecraft/resources/ResourceLocation;)Ljava/lang/String;")
    private static String bons$keptSource(ResourceLocation name, Operation<String> original) {
        return ShaderSources.source(name, original);
    }
}
