package bons.furious.mixin.kubejs_c49;

import bons.furious.patch.kubejs_c49.SkipFileProbes;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.latvian.mods.kubejs.script.data.KubeFileResourcePack;
import java.nio.file.Path;
import java.util.function.Predicate;
import java.util.stream.Stream;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * kubejs_skip_file_probes (KubeJS, LGPL-3.0; 1.21.1 tested build neoforge 2101.7.2-build.381; both sides; trade-off, off
 * by default): a cancellable HEAD inject that ends KubeFileResourcePack.scanForInvalidFiles at once, and a wrapper on the
 * third Stream.filter call of getGenerated (the Files::isReadable filter of the file walk) that leaves the walk unfiltered.
 * What changes: SkipFileProbes.
 *
 * Ported to 1.21.1: both methods moved to KubeFileResourcePack (was GeneratedResourcePack); scanForInvalidFiles is now
 * called once, from KubeJS's constructor at start-up (kubejs/assets on a client, kubejs/data).
 */
@Mixin(value = KubeFileResourcePack.class, remap = false)
public abstract class GeneratedPackProbesMixin {
    @Inject(method = "scanForInvalidFiles(Ljava/lang/String;Ljava/nio/file/Path;)V", at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    private static void bons$kubejsC49SkipScan(String pathName, Path path, CallbackInfo ci) {
        if (SkipFileProbes.enabled) {
            SkipFileProbes.announce();
            ci.cancel();
        }
    }

    @WrapOperation(method = "getGenerated()Ljava/util/Map;",
            at = @At(value = "INVOKE", target = "Ljava/util/stream/Stream;filter(Ljava/util/function/Predicate;)Ljava/util/stream/Stream;", ordinal = 2),
            require = 1, allow = 1)
    private Stream<Path> bons$kubejsC49NoReadProbe(Stream<Path> files, Predicate<Path> readable, Operation<Stream<Path>> original) {
        return SkipFileProbes.enabled ? files : original.call(files, readable);
    }
}
