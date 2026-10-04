package bons.furious.patch.save_writer;

import java.io.File;

/**
 * Bons and Furious switches vanilla_background_saves / vanilla_background_level_dat: stands in for the temporary file of
 * PlayerDataStorage.save and LevelStorageAccess.saveDataTag while the save goes to the writer thread. Minecraft's method
 * only hands it to NbtIo.writeCompressed and Util.safeReplaceFile, both wrapped by the switch, so it never touches the
 * disk: the writer creates the real temporary file (File.createTempFile with the same prefix, suffix and folder) right
 * before it writes it. Never created on disk itself.
 */
public final class DeferredFile extends File {
    final String prefix, suffix;
    final File folder;
    NbtRecording recording;

    public DeferredFile(String prefix, String suffix, File folder) {
        super(folder, prefix + "bons-deferred" + suffix);
        this.prefix = prefix;
        this.suffix = suffix;
        this.folder = folder;
    }
}
