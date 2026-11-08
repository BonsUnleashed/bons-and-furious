package bons.furious.mixin.fancymenu_files_c49;

import bons.furious.patch.fancymenu_files_c49.LocalSourceMemo;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import de.keksuccino.fancymenu.util.file.LocalSourcePathResolver;
import java.util.concurrent.ConcurrentHashMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * fancymenu_local_source_memo (FancyMenu 3.9.14 for NeoForge 1.21.1; client side; trade-off, off by default):
 * LocalSourcePathResolver.resolve answers from this resolver's own memo of successful resolves (LocalSourceMemo); a miss runs
 * the original method and keeps its result; exceptions are thrown as before and never kept.
 */
@Mixin(value = LocalSourcePathResolver.class, remap = false)
public abstract class LocalSourceMemoMixin {
    @Unique
    private final ConcurrentHashMap<String, LocalSourceMemo.Kept> bons$fancymenuFilesC49Resolved = new ConcurrentHashMap<>();

    @WrapMethod(method = "resolve(Ljava/lang/String;)Lde/keksuccino/fancymenu/util/file/LocalSourcePathResolver$ResolvedPath;")
    private LocalSourcePathResolver.ResolvedPath bons$fancymenuFilesC49Resolve(String rawPath, Operation<LocalSourcePathResolver.ResolvedPath> original) {
        return LocalSourceMemo.resolve(this.bons$fancymenuFilesC49Resolved, rawPath, original);
    }
}
