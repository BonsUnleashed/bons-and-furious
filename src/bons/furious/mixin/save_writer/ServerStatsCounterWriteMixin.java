package bons.furious.mixin.save_writer;

import bons.furious.patch.save_writer.SaveWriter;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.io.File;
import net.minecraft.stats.ServerStatsCounter;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * vanilla_background_saves (Minecraft 1.20.1 on Forge 47.4.16, both sides; acts on the thread of a running server):
 * ServerStatsCounter.save (m_12818_) builds the stats JSON text as before (its argument); the writer thread passes it to
 * the same FileUtils.writeStringToFile(file, text) call. Off the running server thread it runs as before after waiting
 * for the writer. The constructor, which reads the file, waits for the writer first. An I/O failure on the writer is
 * logged as the method logs it ("Couldn't save stats", same logger). No Minecraft code is carried.
 */
@Mixin(value = ServerStatsCounter.class, remap = false)
public abstract class ServerStatsCounterWriteMixin {
    @Shadow
    @Final
    private static Logger f_12809_;

    @WrapOperation(method = "m_12818_", at = @At(value = "INVOKE", target = "Lorg/apache/commons/io/FileUtils;writeStringToFile(Ljava/io/File;Ljava/lang/String;)V"))
    private void bons$writeInBackground(File file, String text, Operation<Void> original) {
        if (!SaveWriter.deferSaves()) {
            SaveWriter.drain();
            original.call(file, text);
            return;
        }
        SaveWriter.submitStats(file, text, f_12809_);
    }

    @WrapOperation(method = "<init>", at = @At(value = "INVOKE", target = "Ljava/io/File;isFile()Z"))
    private boolean bons$waitBeforeRead(File file, Operation<Boolean> original) {
        SaveWriter.drain();
        return original.call(file);
    }
}
