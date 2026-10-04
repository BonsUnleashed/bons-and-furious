package bons.furious.mixin.save_writer;

import bons.furious.patch.save_writer.SaveWriter;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.io.File;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.saveddata.SavedData;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * vanilla_background_saves (Minecraft 1.20.1 on Forge 47.4.16, both sides; acts on the thread of a running server):
 * SavedData.save(File) (m_77757_) builds the file's tag as before and, instead of NbtIo.writeCompressed(tag, file)
 * (m_128944_), records the tag's uncompressed bytes now and lets SaveWriter's writer thread gzip and write them with the
 * same stream stack. The rest of the method (setDirty(false)) runs as before. Classes that declare their own save(File)
 * (custom file handling around this call), calls off the server thread, and calls while the server is not running write
 * here and now, after waiting for the writer. Write failures are logged by the writer with this class's logger and
 * Minecraft's message. No Minecraft code is carried.
 */
@Mixin(value = SavedData.class, remap = false)
public abstract class SavedDataWriteMixin {
    @Shadow
    @Final
    private static Logger f_77751_;

    @WrapOperation(method = "m_77757_", at = @At(value = "INVOKE", target = "Lnet/minecraft/nbt/NbtIo;m_128944_(Lnet/minecraft/nbt/CompoundTag;Ljava/io/File;)V"))
    private void bons$writeInBackground(CompoundTag tag, File file, Operation<Void> original) {
        SavedData self = (SavedData) (Object) this;
        if (!SaveWriter.deferSavedData(self)) {
            SaveWriter.drain();
            original.call(tag, file);
            return;
        }
        SaveWriter.submitSavedData(tag, file, self, f_77751_);
    }
}
