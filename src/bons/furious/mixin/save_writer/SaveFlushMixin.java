package bons.furious.mixin.save_writer;

import bons.furious.patch.save_writer.SaveWriter;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.WorldData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * vanilla_background_saves (Minecraft 1.20.1 on Forge 47.4.16, both sides): MinecraftServer.saveAllChunks (m_129885_) with
 * flush = true (/save-all flush, server stop, mods that save before a backup) waits, right after it has written
 * level.dat, until every queued background save is on disk, so the method returns with the same files written as in
 * Minecraft. Without flush nothing changes.
 */
@Mixin(value = MinecraftServer.class, remap = false)
public abstract class SaveFlushMixin {
    @WrapOperation(method = "m_129885_", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/storage/LevelStorageSource$LevelStorageAccess;m_78290_(Lnet/minecraft/core/RegistryAccess;Lnet/minecraft/world/level/storage/WorldData;Lnet/minecraft/nbt/CompoundTag;)V"))
    private void bons$flushAfterLevelData(LevelStorageSource.LevelStorageAccess access, RegistryAccess registries, WorldData data, CompoundTag player,
                                          Operation<Void> original, @Local(argsOnly = true, ordinal = 1) boolean flush) {
        original.call(access, registries, data, player);
        if (flush) SaveWriter.drain();
    }
}
