package bons.furious.mixin.modernfix_pack_prebuild;

import bons.furious.patch.modernfix_pack_prebuild.PackCachePrebuild;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraftforge.forgespi.locating.IModFile;
import net.minecraftforge.resource.PathPackResources;
import net.minecraftforge.resource.ResourcePackLoader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * modernfix_pack_cache_prebuild (Forge 47.4.16, both sides): at the end of ResourcePackLoader.loadResourcePacks (the overload
 * every caller reaches), once modResourcePacks holds the new mod-file packs, PackCachePrebuild queues one ModernFix cache build
 * per pack on its background pool. The method's work is unchanged; Fusion's createPackForMod hook is a different method.
 * Called once at client start, once at server boot, and once per singleplayer world (ModernFix skips repeats per repository).
 */
@Mixin(value = ResourcePackLoader.class, remap = false)
public abstract class ResourcePackLoaderPrebuildMixin {
    @Shadow
    private static Map<IModFile, PathPackResources> modResourcePacks;

    @Inject(method = "loadResourcePacks(Lnet/minecraft/server/packs/repository/PackRepository;Ljava/util/function/Function;)V", at = @At("TAIL"), require = 0)
    private static void bons$queuePackCaches(PackRepository resourcePacks, Function<?, ?> packFinder, CallbackInfo ci) {
        PackCachePrebuild.afterLoad(modResourcePacks);
    }
}
