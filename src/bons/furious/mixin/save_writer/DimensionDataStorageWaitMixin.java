package bons.furious.mixin.save_writer;

import bons.furious.patch.save_writer.SaveWriter;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.io.File;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.storage.DimensionDataStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * vanilla_background_saves (Minecraft 1.20.1 on Forge 47.4.16, both sides): DimensionDataStorage reads a saved-data file
 * only for a name it has not loaded yet (readSavedData m_164868_, before its File.exists check) or when a mod calls
 * readTagFromDisk (m_78158_); both first wait until every queued background save is written, so they read what Minecraft
 * would have read. Nothing else changes.
 */
@Mixin(value = DimensionDataStorage.class, remap = false)
public abstract class DimensionDataStorageWaitMixin {
    @WrapOperation(method = "m_164868_", at = @At(value = "INVOKE", target = "Ljava/io/File;exists()Z"))
    private boolean bons$waitBeforeRead(File file, Operation<Boolean> original) {
        SaveWriter.drain();
        return original.call(file);
    }

    @Inject(method = "m_78158_", at = @At("HEAD"))
    private void bons$waitBeforeReadTag(String name, int version, CallbackInfoReturnable<CompoundTag> cir) {
        SaveWriter.drain();
    }
}
