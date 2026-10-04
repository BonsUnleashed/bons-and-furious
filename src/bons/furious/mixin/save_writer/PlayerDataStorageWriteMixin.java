package bons.furious.mixin.save_writer;

import bons.furious.patch.save_writer.DeferredFile;
import bons.furious.patch.save_writer.SaveWriter;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import java.io.File;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.PlayerDataStorage;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * vanilla_background_saves (Minecraft 1.20.1 on Forge 47.4.16, both sides; acts on the thread of a running server):
 * PlayerDataStorage.save (m_78433_) builds the player's tag as before; File.createTempFile, NbtIo.writeCompressed and
 * Util.safeReplaceFile move to the writer thread (it creates the same temporary file in playerdata/, writes the recorded
 * bytes into it and replaces <uuid>.dat, keeping <uuid>.dat_old, exactly as the method does). Only while Forge's
 * PlayerEvent.SaveToFile, which the method fires after the write, has no listener (none in this pack); otherwise, and
 * off the running server thread, the method runs as before after waiting for the writer. A failure on the writer is
 * logged as the method's catch logs it ("Failed to save player data for {name}", same logger). load (m_78435_) waits for
 * the writer before it looks at the file. No Minecraft code is carried.
 */
@Mixin(value = PlayerDataStorage.class, remap = false)
public abstract class PlayerDataStorageWriteMixin {
    @Shadow
    @Final
    private static Logger f_78426_;

    @WrapOperation(method = "m_78433_", at = @At(value = "INVOKE", target = "Ljava/io/File;createTempFile(Ljava/lang/String;Ljava/lang/String;Ljava/io/File;)Ljava/io/File;"))
    private File bons$deferTemporaryFile(String prefix, String suffix, File folder, Operation<File> original) {
        if (SaveWriter.deferPlayerData()) return new DeferredFile(prefix, suffix, folder);
        SaveWriter.drain();
        return original.call(prefix, suffix, folder);
    }

    @WrapOperation(method = "m_78433_", at = @At(value = "INVOKE", target = "Lnet/minecraft/nbt/NbtIo;m_128944_(Lnet/minecraft/nbt/CompoundTag;Ljava/io/File;)V"))
    private void bons$recordPlayerTag(CompoundTag tag, File file, Operation<Void> original) {
        if (file instanceof DeferredFile deferred) SaveWriter.record(tag, deferred);
        else original.call(tag, file);
    }

    @WrapOperation(method = "m_78433_", at = @At(value = "INVOKE", target = "Lnet/minecraft/Util;m_137462_(Ljava/io/File;Ljava/io/File;Ljava/io/File;)V"))
    private void bons$replaceInBackground(File current, File latest, File old, Operation<Void> original, @Local(argsOnly = true) Player player) {
        if (!(latest instanceof DeferredFile deferred)) {
            original.call(current, latest, old);
            return;
        }
        String name = player.m_7755_().getString();
        SaveWriter.submitReplace(deferred, current, old, e -> f_78426_.warn("Failed to save player data for {}", name));
    }

    @WrapOperation(method = "m_78435_", at = @At(value = "INVOKE", target = "Ljava/io/File;exists()Z"))
    private boolean bons$waitBeforeLoad(File file, Operation<Boolean> original) {
        SaveWriter.drain();
        return original.call(file);
    }
}
