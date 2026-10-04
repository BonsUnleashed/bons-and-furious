package bons.furious.mixin.save_writer;

import bons.furious.patch.save_writer.SaveWriter;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import java.io.BufferedWriter;
import java.nio.charset.Charset;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import net.minecraft.server.PlayerAdvancements;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * vanilla_background_saves (Minecraft 1.20.1 on Forge 47.4.16, both sides; acts on the thread of a running server):
 * PlayerAdvancements.save (m_135991_) builds the JSON tree as before and Gson writes it as before, but into a
 * BufferedWriter over a text capture instead of the file; when the method closes that writer, the writer thread runs
 * FileUtil.createDirectoriesSafe(folder) and writes the same text through Files.newBufferedWriter(path, UTF-8) (the same
 * 8192-char buffer flushes, so the same bytes). Off the running server thread it runs as before after waiting for the
 * writer. load (m_136006_) waits for the writer before it looks at the file. An I/O failure on the writer is logged as
 * the method logs it ("Couldn't save player advancements to {path}", same logger). No Minecraft code is carried.
 */
@Mixin(value = PlayerAdvancements.class, remap = false)
public abstract class PlayerAdvancementsWriteMixin {
    @Shadow
    @Final
    private static Logger f_135958_;

    @WrapOperation(method = "m_135991_", at = @At(value = "INVOKE", target = "Lnet/minecraft/FileUtil;m_257659_(Ljava/nio/file/Path;)V"))
    private void bons$deferFolder(Path folder, Operation<Void> original, @Share("bons$folder") LocalRef<Path> deferredFolder) {
        if (SaveWriter.deferSaves()) {
            deferredFolder.set(folder);
            return;
        }
        SaveWriter.drain();
        original.call(folder);
    }

    @WrapOperation(method = "m_135991_", at = @At(value = "INVOKE", target = "Ljava/nio/file/Files;newBufferedWriter(Ljava/nio/file/Path;Ljava/nio/charset/Charset;[Ljava/nio/file/OpenOption;)Ljava/io/BufferedWriter;"))
    private BufferedWriter bons$captureText(Path path, Charset charset, OpenOption[] options, Operation<BufferedWriter> original,
                                           @Share("bons$folder") LocalRef<Path> deferredFolder) {
        Path folder = deferredFolder.get();
        if (folder == null) return original.call(path, charset, options);
        return SaveWriter.captureText(folder, path, charset, options, f_135958_);
    }

    @WrapOperation(method = "m_136006_", at = @At(value = "INVOKE", target = "Ljava/nio/file/Files;isRegularFile(Ljava/nio/file/Path;[Ljava/nio/file/LinkOption;)Z"))
    private boolean bons$waitBeforeLoad(Path path, LinkOption[] options, Operation<Boolean> original) {
        SaveWriter.drain();
        return original.call(path, options);
    }
}
