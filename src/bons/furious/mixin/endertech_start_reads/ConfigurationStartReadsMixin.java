package bons.furious.mixin.endertech_start_reads;

import bons.furious.patch.endertech_start_reads.StartReads;
import com.endertech.minecraft.forge.configs.Configuration;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.io.File;
import java.io.FileInputStream;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * forgeendertech_start_config_reads_once (ForgeEndertech 11.1.6.0; both sides: the server and the integrated server).
 *
 * Wrappers in Configuration.load() and Configuration.save(), no ForgeEndertech code: load() as a whole (StartReads opens
 * a session while a server is starting), its File.mkdirs / File.exists / File.canRead calls (answered from the first
 * load of that path inside a session that replays it), its new FileInputStream(file) (the first load's bytes are kept;
 * a replay reads them from memory) and save() (the path is forgotten). The parser and everything it builds are
 * ForgeEndertech's own and run on every load.
 */
@Mixin(value = Configuration.class, remap = false)
public abstract class ConfigurationStartReadsMixin {
    @Shadow
    File file;

    @WrapMethod(method = "load")
    private void bons$startRead(Operation<Void> original) {
        StartReads.load(this.file, original);
    }

    @WrapOperation(method = "load", require = 1, allow = 1, at = @At(value = "INVOKE", target = "Ljava/io/File;mkdirs()Z"))
    private boolean bons$startMkdirs(File folder, Operation<Boolean> original) {
        return StartReads.mkdirs(folder, original);
    }

    @WrapOperation(method = "load", require = 1, allow = 1, at = @At(value = "INVOKE", target = "Ljava/io/File;exists()Z"))
    private boolean bons$startExists(File config, Operation<Boolean> original) {
        return StartReads.exists(config, original);
    }

    @WrapOperation(method = "load", require = 1, allow = 1, at = @At(value = "INVOKE", target = "Ljava/io/File;canRead()Z"))
    private boolean bons$startCanRead(File config, Operation<Boolean> original) {
        return StartReads.canRead(config, original);
    }

    @WrapOperation(method = "load", require = 1, allow = 1, at = @At(value = "NEW", target = "(Ljava/io/File;)Ljava/io/FileInputStream;"))
    private FileInputStream bons$startOpen(File config, Operation<FileInputStream> original) {
        return StartReads.open(config, original);
    }

    @WrapMethod(method = "save()V")
    private void bons$startSaved(Operation<Void> original) {
        try {
            original.call();
        } finally {
            StartReads.saved(this.file);
        }
    }
}
