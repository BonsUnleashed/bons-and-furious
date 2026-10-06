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
 * files), as Minecraft's synchronous saved-data writes would have. Since 1.0.34 it also waits before posting Forge's
 * LevelEvent.Save, so that event's listeners find the level's saved-data files written. Without flush nothing changes.
 */
@Mixin(value = ServerLevel.class, remap = false)
public abstract class LevelSaveFlushMixin {
    @Inject(method = "m_8643_", at = @At("RETURN"))
    private void bons$flushSavedData(ProgressListener progress, boolean flush, boolean skipSave, CallbackInfo ci) {
        if (flush) SaveWriter.drain();
    }

    /**
     * 1.0.34: with flush, the queued saved-data files are written before LevelEvent.Save is posted, as Minecraft wrote them
     * before it; the RETURN wait above still covers saves the listeners queue. require = 0: if another mod's mixin took the
     * post away, the RETURN wait alone applies, as in 1.0.33.
     */
    @Inject(method = "m_8643_", require = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraftforge/eventbus/api/IEventBus;post(Lnet/minecraftforge/eventbus/api/Event;)Z"))
    private void bons$flushBeforeSaveEvent(ProgressListener progress, boolean flush, boolean skipSave, CallbackInfo ci) {
        if (flush) SaveWriter.drain();
    }
}
