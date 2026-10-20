package bons.furious.mixin.modernfix_pack_prebuild;

import bons.furious.patch.modernfix_pack_prebuild.PackCachePrebuild;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.nio.file.Path;
import java.util.function.Function;
import net.minecraft.server.packs.PackType;
import net.minecraftforge.resource.PathPackResources;
import org.embeddedt.modernfix.resources.PackResourcesCacheEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * modernfix_pack_cache_prebuild (ModernFix 5.27.77 on Forge 47.4.16, both sides): inside generateResourceCache, the method
 * ModernFix's ForgePathPackResourcesMixin adds to Forge's PathPackResources, the `new PackResourcesCacheEngine(retriever)` that
 * creates a pack's cache on its first lookup. PackCachePrebuild hands over the engine its background task built for this pack
 * (the same tree; ModernFix's method still stores it in the pack, under its own lock), or lets the constructor run. Priority 1500:
 * Mixin applies this after ModernFix's mixin (priority 1100), whose method it changes. Without ModernFix's mixin (its option
 * mixin.perf.resourcepacks off) generateResourceCache does not exist, and Mixin stops the game when an injector finds no target
 * method at all, whatever require says; so Forge's own toString (always there, never creating an engine) is listed as a second
 * target: with require = 0 the injector then has nothing to change, and PackCachePrebuild queues nothing (it checks that the
 * hand-over is in generateResourceCache before it builds anything).
 */
@Mixin(value = PathPackResources.class, remap = false, priority = 1500)
public abstract class PathPackResourcesPrebuildMixin {
    @WrapOperation(method = {"generateResourceCache", "toString"}, require = 0, expect = 0, at = @At(value = "NEW",
            target = "(Ljava/util/function/Function;)Lorg/embeddedt/modernfix/resources/PackResourcesCacheEngine;"))
    private PackResourcesCacheEngine bons$prebuiltEngine(Function<PackType, Path> retriever, Operation<PackResourcesCacheEngine> original) {
        return PackCachePrebuild.engine(this, retriever, original);
    }
}
