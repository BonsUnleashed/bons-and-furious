package bons.furious.mixin.save_writer;

import bons.furious.patch.save_writer.DeferredFile;
import bons.furious.patch.save_writer.SaveWriter;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import java.io.File;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * vanilla_background_level_dat (Minecraft 1.20.1 on Forge 47.4.16, both sides; acts on the thread of a running server):
 * LevelStorageAccess.saveDataTag (m_78290_) builds level.dat's tag as before (WorldData.createTag and Forge's
 * writeAdditionalLevelSaveData); File.createTempFile("level", ".dat", world), NbtIo.writeCompressed and
 * Util.safeReplaceFile(level.dat, temp, level.dat_old) move to Bons and Furious's background writer, which runs them in
 * the same order with the same bytes. Off the running server thread (world creation, server stop) it runs as before after
 * waiting for the writer. A failure on the writer is logged as the method logs it ("Failed to save level {world}", the
 * LevelStorageSource logger). The readers of level.dat in this class and close/deleteLevel/makeWorldBackup/renameLevel
 * wait for the writer first. No Minecraft code is carried.
 */
@Mixin(value = LevelStorageSource.LevelStorageAccess.class, remap = false)
public abstract class LevelDatWriteMixin {
    @WrapOperation(method = "m_78290_", at = @At(value = "INVOKE", target = "Ljava/io/File;createTempFile(Ljava/lang/String;Ljava/lang/String;Ljava/io/File;)Ljava/io/File;"))
    private File bons$deferLevelTemporaryFile(String prefix, String suffix, File world, Operation<File> original, @Share("bons$world") LocalRef<File> deferredWorld) {
        if (SaveWriter.deferLevelDat()) {
            deferredWorld.set(world);
            return new DeferredFile(prefix, suffix, world);
        }
        SaveWriter.drain();
        return original.call(prefix, suffix, world);
    }

    @WrapOperation(method = "m_78290_", at = @At(value = "INVOKE", target = "Lnet/minecraft/nbt/NbtIo;m_128944_(Lnet/minecraft/nbt/CompoundTag;Ljava/io/File;)V"))
    private void bons$recordLevelTag(CompoundTag tag, File file, Operation<Void> original) {
        if (file instanceof DeferredFile deferred) SaveWriter.record(tag, deferred);
        else original.call(tag, file);
    }

    @WrapOperation(method = "m_78290_", at = @At(value = "INVOKE", target = "Lnet/minecraft/Util;m_137462_(Ljava/io/File;Ljava/io/File;Ljava/io/File;)V"))
    private void bons$replaceLevelDatInBackground(File current, File latest, File old, Operation<Void> original, @Share("bons$world") LocalRef<File> deferredWorld) {
        if (!(latest instanceof DeferredFile deferred)) {
            original.call(current, latest, old);
            return;
        }
        File world = deferredWorld.get();
        SaveWriter.submitReplace(deferred, current, old, e -> SaveWriter.LEVEL_STORAGE_LOGGER.error("Failed to save level {}", world, e));
    }

    @Inject(method = {"close", "m_78311_", "readAdditionalLevelSaveData"}, at = @At("HEAD"))
    private void bons$waitBeforeLevelFiles(CallbackInfo ci) {
        SaveWriter.drain();
    }

    @Inject(method = {"m_78312_"}, at = @At("HEAD"))
    private void bons$waitBeforeBackup(CallbackInfoReturnable<Long> cir) {
        SaveWriter.drain();
    }

    @Inject(method = "m_78297_", at = @At("HEAD"))
    private void bons$waitBeforeRename(String name, CallbackInfo ci) {
        SaveWriter.drain();
    }

    @Inject(method = {"m_78308_", "m_246049_", "m_247706_"}, at = @At("HEAD"))
    private void bons$waitBeforeLevelRead(CallbackInfoReturnable<?> cir) {
        SaveWriter.drain();
    }
}
