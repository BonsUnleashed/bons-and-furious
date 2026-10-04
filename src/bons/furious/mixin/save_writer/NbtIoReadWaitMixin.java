package bons.furious.mixin.save_writer;

import bons.furious.patch.save_writer.SaveWriter;
import java.io.File;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.StreamTagVisitor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * vanilla_background_saves (Minecraft 1.20.1 on Forge 47.4.16, both sides): NbtIo's file readers (readCompressed(File)
 * m_128937_, parseCompressed(File, visitor) m_202487_, read(File) m_128953_) and file writers (writeCompressed(tag, File)
 * m_128944_, write(tag, File) m_128955_), which Minecraft's loaders and other mods use for .dat files, first wait until
 * every queued background save is written: a read sees what Minecraft would have written, and a direct write by any other
 * code lands after the queued ones, in Minecraft's order. A volatile read when nothing is queued; nothing else changes.
 */
@Mixin(value = NbtIo.class, remap = false)
public abstract class NbtIoReadWaitMixin {
    @Inject(method = "m_128937_", at = @At("HEAD"))
    private static void bons$waitBeforeReadCompressed(File file, CallbackInfoReturnable<CompoundTag> cir) {
        SaveWriter.drain();
    }

    @Inject(method = "m_202487_", at = @At("HEAD"))
    private static void bons$waitBeforeParseCompressed(File file, StreamTagVisitor visitor, CallbackInfo ci) {
        SaveWriter.drain();
    }

    @Inject(method = "m_128953_", at = @At("HEAD"))
    private static void bons$waitBeforeRead(File file, CallbackInfoReturnable<CompoundTag> cir) {
        SaveWriter.drain();
    }

    @Inject(method = "m_128944_", at = @At("HEAD"))
    private static void bons$waitBeforeWriteCompressed(CompoundTag tag, File file, CallbackInfo ci) {
        SaveWriter.drain();
    }

    @Inject(method = "m_128955_", at = @At("HEAD"))
    private static void bons$waitBeforeWrite(CompoundTag tag, File file, CallbackInfo ci) {
        SaveWriter.drain();
    }
}
