package bons.furious.mixin.kubejs_c49;

import bons.furious.patch.kubejs_c49.SkipFileProbes;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.latvian.mods.kubejs.script.data.GeneratedResourcePack;
import java.nio.file.Path;
import java.util.function.Predicate;
import java.util.stream.Stream;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * kubejs_skip_file_probes (KubeJS 2001.6.5-build.26, LGPL-3.0; both sides; trade-off, off by default): a cancellable HEAD
 * inject that ends GeneratedResourcePack.scanForInvalidFiles at once, and a wrapper on the third Stream.filter call of
 * getGenerated (the Files::isReadable filter of the file walk) that leaves the walk unfiltered. What changes: SkipFileProbes.
 */
@Mixin(value = GeneratedResourcePack.class, remap = false)
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
