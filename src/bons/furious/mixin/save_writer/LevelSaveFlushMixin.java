package bons.furious.mixin.save_writer;

import bons.furious.patch.save_writer.SaveWriter;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProgressListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * vanilla_background_saves (Minecraft 1.20.1 on Forge 47.4.16, both sides): ServerLevel.save (m_8643_) with flush = true
 * returns only after every queued background save is on disk (mods may call it directly before copying a dimension's
 * files), as Minecraft's synchronous saved-data writes would have. Without flush nothing changes.
 */
@Mixin(value = ServerLevel.class, remap = false)
public abstract class LevelSaveFlushMixin {
    @Inject(method = "m_8643_", at = @At("RETURN"))
    private void bons$flushSavedData(ProgressListener progress, boolean flush, boolean skipSave, CallbackInfo ci) {
        if (flush) SaveWriter.drain();
    }
}
