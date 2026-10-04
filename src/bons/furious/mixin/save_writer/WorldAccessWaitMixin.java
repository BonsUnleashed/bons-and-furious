package bons.furious.mixin.save_writer;

import bons.furious.patch.save_writer.SaveWriter;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * vanilla_background_saves (Minecraft 1.20.1 on Forge 47.4.16, both sides): before LevelStorageAccess releases the world's
 * session lock (close), deletes the world (deleteLevel m_78311_), zips it (makeWorldBackup m_78312_) or rewrites its
 * name in level.dat (renameLevel m_78297_), every queued background save is written. Nothing else changes.
 */
@Mixin(value = LevelStorageSource.LevelStorageAccess.class, remap = false)
public abstract class WorldAccessWaitMixin {
    @Inject(method = "close", at = @At("HEAD"))
    private void bons$waitBeforeUnlock(CallbackInfo ci) {
        SaveWriter.drain();
    }

    @Inject(method = "m_78311_", at = @At("HEAD"))
    private void bons$waitBeforeDelete(CallbackInfo ci) {
        SaveWriter.drain();
    }

    @Inject(method = "m_78312_", at = @At("HEAD"))
    private void bons$waitBeforeBackup(CallbackInfoReturnable<Long> cir) {
        SaveWriter.drain();
    }

    @Inject(method = "m_78297_", at = @At("HEAD"))
    private void bons$waitBeforeRename(String name, CallbackInfo ci) {
        SaveWriter.drain();
    }
}
